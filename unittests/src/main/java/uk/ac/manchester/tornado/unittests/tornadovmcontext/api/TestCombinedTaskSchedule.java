/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.tornadovmcontext.api;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * The unit-tests in this class check that TornadoVM TaskSchedule API can
 * combine multiple tasks, which can either exploit the {@link TornadoVMContext}
 * features or adhere to the original TornadoVM annotations
 * {@link uk.ac.manchester.tornado.api.annotations.Parallel} or
 * {@link uk.ac.manchester.tornado.api.annotations.Reduce}.
 * 
 * The following tests implement a single TaskSchedule that has three
 * consecutive tasks: t0: Vector Addition, t1: Vector Multiplication and t2:
 * Vector Subtraction.
 */
public class TestCombinedTaskSchedule extends TornadoTestBase {

    /**
     * Method that performs the vector addition of two arrays and stores the result
     * in a third array. This method uses the
     * {@link uk.ac.manchester.tornado.api.annotations.Parallel} annotation.
     * 
     * @param a
     *            input array
     * @param b
     *            input array
     * @param c
     *            output array
     */
    public static void vectorAddV1(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    /**
     * Method that performs the vector addition of two arrays and stores the result
     * in a third array. This method uses the {@link TornadoVMContext} thread
     * identifier.
     *
     * @param a
     *            input array
     * @param b
     *            input array
     * @param c
     *            output array
     */
    public static void vectorAddV2(TornadoVMContext context, int[] a, int[] b, int[] c) {
        c[context.threadIdx] = a[context.threadIdx] + b[context.threadIdx];
    }

    /**
     * Method that performs the vector multiplication of two arrays and stores the
     * result in a third array. This method uses the
     * {@link uk.ac.manchester.tornado.api.annotations.Parallel} annotation.
     *
     * @param a
     *            input array
     * @param b
     *            input array
     * @param c
     *            output array
     */
    public static void vectorMulV1(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] * b[i];
        }
    }

    /**
     * Method that performs the vector multiplication of two arrays and stores the
     * result in a third array. This method uses the {@link TornadoVMContext} thread
     * identifier.
     *
     * @param a
     *            input array
     * @param b
     *            input array
     * @param c
     *            output array
     */
    public static void vectorMulV2(TornadoVMContext context, int[] a, int[] b, int[] c) {
        c[context.threadIdx] = a[context.threadIdx] * b[context.threadIdx];
    }

    /**
     * Method that performs the vector subtraction of two arrays and stores the
     * result in a third array. This method uses the
     * {@link uk.ac.manchester.tornado.api.annotations.Parallel} annotation.
     *
     * @param a
     *            input array
     * @param b
     *            input array
     * @param c
     *            output array
     */
    public static void vectorSubV1(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] - b[i];
        }
    }

    /**
     * Method that performs the vector subtraction of two arrays and stores the
     * result in a third array. This method uses the {@link TornadoVMContext} thread
     * identifier.
     *
     * @param a
     *            input array
     * @param b
     *            input array
     * @param c
     *            output array
     */
    public static void vectorSubV2(TornadoVMContext context, int[] a, int[] b, int[] c) {
        c[context.threadIdx] = a[context.threadIdx] - b[context.threadIdx];
    }

    /**
     * In this test, all tasks use the TaskSchedule API, and only t0 uses the
     * {@link GridTask} and {@link WorkerGrid} to deploy a specific number of
     * threads.
     */
    @Test
    public void combinedAPI01() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s01.t0", worker);

        TaskSchedule s01 = new TaskSchedule("s01") //
                .streamIn(a, b) //
                .task("t0", TestCombinedTaskSchedule::vectorAddV1, a, b, cTornado) //
                .task("t1", TestCombinedTaskSchedule::vectorMulV1, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskSchedule::vectorSubV1, cTornado, b, cTornado) //
                .streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(size, 1, 1);
        s01.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    /**
     * In this test, all tasks use the {@link TornadoVMContext} within the
     * TaskSchedule API, and all tasks share the same {@link GridTask} and
     * {@link WorkerGrid} to deploy a specific number of threads.
     */
    @Test
    public void combinedAPI02() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s02.t0", worker);
        gridTask.setWorkerGrid("s02.t1", worker);
        gridTask.setWorkerGrid("s02.t2", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s02 = new TaskSchedule("s02") //
                .streamIn(a, b) //
                .task("t0", TestCombinedTaskSchedule::vectorAddV2, context, a, b, cTornado) //
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado).task("t2", TestCombinedTaskSchedule::vectorSubV2, context, cTornado, b, cTornado) //
                .streamOut(cTornado);
        s02.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    /**
     * In this test, all tasks use the {@link TornadoVMContext} within the
     * TaskSchedule API, and tasks t1 and t2 share the same {@link GridTask} and
     * {@link WorkerGrid} to deploy a specific number of threads.
     */
    @Test
    public void combinedAPI03() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s03.t1", worker);
        gridTask.setWorkerGrid("s03.t2", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s03 = new TaskSchedule("s03") //
                .streamIn(a, b) //
                .task("t0", TestCombinedTaskSchedule::vectorAddV1, a, b, cTornado) //
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskSchedule::vectorSubV2, context, cTornado, b, cTornado) //
                .streamOut(cTornado);
        s03.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    /**
     * In this test, t0 and t1 use the {@link TornadoVMContext} within the
     * TaskSchedule API, and share the same {@link GridTask} and {@link WorkerGrid}
     * to deploy a specific number of threads. While, t2 uses the TaskSchedule API.
     */
    @Test
    public void combinedAPI04() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s04.t0", worker);
        gridTask.setWorkerGrid("s04.t1", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s04 = new TaskSchedule("s04") //
                .streamIn(a, b) //
                .task("t0", TestCombinedTaskSchedule::vectorAddV2, context, a, b, cTornado) //
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskSchedule::vectorSubV1, cTornado, b, cTornado) //
                .streamOut(cTornado);
        s04.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    /**
     * In this test, t0 and t1 use the {@link TornadoVMContext} within the
     * TaskSchedule API, and use separate {@link GridTask} and {@link WorkerGrid} to
     * deploy different number of threads. While, t2 uses the TaskSchedule API.
     */
    @Test
    public void combinedAPI05() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid workerT0 = new WorkerGrid1D(size);
        WorkerGrid workerT1 = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s05.t0", workerT0);
        gridTask.setWorkerGrid("s05.t1", workerT1);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s05 = new TaskSchedule("s05") //
                .streamIn(a, b) //
                .task("t0", TestCombinedTaskSchedule::vectorAddV2, context, a, b, cTornado) //
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskSchedule::vectorSubV1, cTornado, b, cTornado) //
                .streamOut(cTornado);
        // Change the dimension of the Grids
        workerT0.setGlobalWork(size, 1, 1);
        workerT0.setLocalWork(size / 2, 1, 1);
        workerT1.setGlobalWork(size, 1, 1);
        workerT1.setLocalWorkToNull();
        s05.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }
}
