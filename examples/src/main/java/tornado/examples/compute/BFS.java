/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Juan Fumero
 *
 */
package tornado.examples.compute;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import tornado.api.Parallel;
import tornado.common.TornadoDevice;
import tornado.runtime.TornadoRuntime;
import tornado.runtime.api.TaskSchedule;

// Parallel Implementation of the BFS
public class BFS {

    int[] vertices;
    int[] adjacencyMatrix;    
    int[] modify;
    int[] currentDepth;

    
    /**
     * Set to one the connection between node from and node to into the adjacency matrix.
     *
     * @param from
     * @param to
     * @param graph
     * @param N
     */
    public static void connect(int from, int to, int[] graph, int N) {
        if (from != to && (graph[from * N + to] == 0)) {
            graph[from * N + to] = 1;
        }
    }

    /**
     * It builds a simple graph just for showing the example.
     *
     * @param adjacencyMatrix
     * @param numNodes
     */
    public static void initilizeAdjacencyMatrixSimpleGraph(int[] adjacencyMatrix, int numNodes) {
        Arrays.fill(adjacencyMatrix, 0);
        connect(0, 1, adjacencyMatrix, numNodes);
        connect(0, 4, adjacencyMatrix, numNodes);
        connect(1, 2, adjacencyMatrix, numNodes);
        connect(2, 3, adjacencyMatrix, numNodes);
        connect(2, 4, adjacencyMatrix, numNodes);
        connect(3, 4, adjacencyMatrix, numNodes);
    }
    
    public static void generateRandomGraph(int[] adjacencyMatrix, int numNodes) {
        Random r = new Random();
        for (int k = 0; k < numNodes; k++) {
            //IntStream from = r.ints(numNodes, 0, numNodes);
            int bound = r.nextInt(numNodes);
            IntStream to = r.ints(bound, 0, numNodes);
            //int[] f = from.toArray();
            int[] t = to.toArray();
            for (int i = 0; i < t.length; i++) {
                connect(k, t[i], adjacencyMatrix, numNodes);
            }
        }
    }

    private static void initializeVertices(int numNodes, int[] vertices, int root) {
        for (@Parallel int i = 0; i < numNodes; i++) {
            if (i == root) {
                vertices[i] = 0;
            } else {
                vertices[i] = -1;
            }
        }
    }
    
    private static void runBFS(int[] vertices, int[] adjacencyMatrix, int numNodes, int[] h_true, int[] currentDepth) {
        for (@Parallel int from = 0; from < numNodes; from++) {
            for (@Parallel int to = 0; to < numNodes; to++) {
                int elementAccess = from * numNodes + to;

                if (adjacencyMatrix[elementAccess] == 1) {
                    int dfirst = vertices[from];
                    int dsecond = vertices[to];
                    if ((currentDepth[0] == dfirst) && (dsecond == -1)) {
                        vertices[to] = dfirst + 1;
                        h_true[to] = 0;
                    } 
                    //if ((currentDepth[0] == dsecond) && (dfirst == -1)) {
                    //    //System.out.println("B: " + Arrays.toString(vertices));
                    //    vertices[from] = dsecond + 1;
                    //    //System.out.println("\t updating b: " + from + " with: " + (dsecond + 1));
                    //    h_true[0] = 0;
                   // }
                }
            }
        }
    }
    
    public void tornadoBFS(int root, int numberOfNodes) throws IOException {
        int numNodes = numberOfNodes;
        
        vertices = new int[numNodes];
        adjacencyMatrix = new int[numNodes * numNodes];

        initilizeAdjacencyMatrixSimpleGraph(adjacencyMatrix, numNodes);
        //generateRandomGraph(adjacencyMatrix, numNodes);

        // Step 1: vertices initialisation
        initializeVertices(numNodes, vertices, root);
        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", BFS::initializeVertices, numNodes, vertices, root);
        s0.streamOut(vertices).execute();
        
        modify = new int[numNodes];
        Arrays.fill(modify, 1);
        
        currentDepth = new int[] {0 };
        
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskSchedule s1 = new TaskSchedule("s1");
        s1.streamIn(vertices, adjacencyMatrix, modify,currentDepth).mapAllTo(device);
        s1.task("t1", BFS::runBFS, vertices, adjacencyMatrix, numNodes, modify, currentDepth);
        s1.streamOut(vertices, modify);
        
        boolean done = false;
        
        while (!done) {
            // 2. Parallel BFS
            boolean allDone = true;
            System.out.println("Current Depth: " + currentDepth[0]);
            System.out.println("\tModify? Before: " + Arrays.toString(modify));
            //runBFS(vertices, adjacencyMatrix, numNodes, modify, currentDepth);
            s1.execute();
            currentDepth[0]++;
            for(int i = 0; i < modify.length; i++) {
                if (modify[i] == 0) {
                    allDone &= false;
                    break;
                }
            }
            System.out.println("\tModify: " + Arrays.toString(modify));

            if (allDone) {
                done = true;
            }
            System.out.println("\tPartial Solution: " + Arrays.toString(vertices));
            Arrays.fill(modify, 1);
        }
        System.out.println("Solution: " + Arrays.toString(vertices));
    }
    
    public static void main(String[] args) throws IOException {
        new BFS().tornadoBFS(0, 5);
    }

}
