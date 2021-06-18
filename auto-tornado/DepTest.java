/*
Test cases for Loop Dependence Analysis.
Before saxpy: Self-written tests (Rishi Sharma)
saxpy and below: TornadoVM examples
 */

import java.util.Arrays;

public class DepTest {
    public static void main(String[] args) {
        simpleDep1(1000);
        simpleDep2(100000);
        simpleDep3(new int[5000]);
        simpleNoDep0(new int[1000]);
        simpleNoDep1(new int[5000]);
        simpleNoDep2(new int[5000]);
        simpleNoDepWithConstant(new float[5000], new float[5000]);
        functionCallNotIndexPure0(59293293);
        functionCallNotIndexPure1(59293293);
        functionCallNotIndexPure2(59293293);
        functionCallNotIndexImpure0(232);
        functionCallNotIndexImpure1(232);
        functionCallNotIndexImpure2(232);
        functionCallNotIndexImpure3(232);
        functionCallNotIndexImpure4(232);
        functionCallNotIndexImpure5(232);
        functionCallNotIndexImpure6(232);
        functionCallNotIndexImpure7(232);
        functionCallNotIndexImpure8(232);
        functionCallNotIndexImpure9(232);
        functionCallIndex(59293293);
        stackVariableDep1(24, 31, new int[5101], new int[5101], 600);
        multiDimArrayNoDep1(35, 42, new int[200][400], new int[400][200]);
        multiDimArrayDep1(31, 49, new int[200][400]);
        nestedDep1(new int[2000]);
        nestedNoDep1(new int[2000]);
        saxpy((float) 0.5, new float[1000], new float[1000]);
        hilberComputationJava(new float[51*59],51, 59);
        vectorAddition(new int[10000], new int[10000], new int[10000]);
        initializeVerticesNoDep(new int[10000], 25);
        short[] mb = mandelbrot(10000);
        System.out.println(Arrays.toString(mb));
        matrixMultiplication(new float[100*100], new float[100*100], new float[100*100], 100);
        computeMontecarlo(new float[10000], 560);
        run2DConvolution(3, 3, new float[256], new float[256]);
        fdtd(2, 3, new float[3], new float[6], new float[22], 4);
        System.out.println(multiMandelbrot(100000)[1000][25]);
        BinarySearchTree[] binarySearchTrees = getTrees(102023);
    }

    public static int identity(int i) {
        /*
        Not analysed.
        No loops.
         */
        return i;
    }

    public static void simpleDep1(int n) {
        int[] ar = new int[n];
        for (int i = 1; i<n; i++) {
            /*
            WAW Dependence.
            Dependence found by Z3.
            */
            ar[n-1] = i;
        }
    }

    public static void simpleDep2(int n) {
        int[] ar = new int[n];
        for (int i = 1; i<n; i++) {
            /*
            RAW Dependence
            Dependence found by Z3.
            */
            ar[i] += ar[i-1];
        }
    }

    public static void simpleDep3(int[] a) {
        for (int i = 1; i<a.length; i++) {
            /*
            RAW + WAR + WAW Dependence
            Dependence found by Z3.
            */
            int temp = a[i];
            a[i] = a[i-1];
            a[i-1] = temp;
        }
    }

    public static void simpleNoDep0(int[] ar) {
        for (int i = 1; i<5; i++) {
            /*
            No Dependence
            Z3 Returns Status.Unsatisfiable
            */
            ar[i*3] = i;
        }
    }

    public static void simpleNoDep1(int[] a) {
        for (int i = 0; i<a.length; i++) {
            /*
            No Dependence
            Z3 Returns Status.Unsatisfiable
            */
            a[i] = i * a[i];
        }
    }

    public static void simpleNoDep2(int[] a) {
        for (int i = 1; i<a.length; i++) {
            /*
            No Dependence
            Z3 Returns Status.Unsatisfiable
            */
            a[i-1] = i * a[i-1];
        }
    }

    private static void simpleNoDepWithConstant(float[] x, float[] y) {
        for (int i = 0; i < y.length; i++) {
            /*
            No Dependence
            Z3 Returns Status.Unsatisfiable
            */
            y[i] = x[i] + 100;
        }
    }

    private static void functionCallNotIndexPure0(int k) {
        int[] a = new int[k];
        for (int i=0; i<k; ++i) {
            identity(i);
            a[i] = i;
        }
    }

    private static void functionCallNotIndexPure1(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.pureFun(i);
        }
    }

    private static void functionCallNotIndexPure2(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.callPureFun(i);
        }
    }

    private static void functionCallNotIndexImpure0(int k) {
        int[] a = new int[k];
        for (int i=0; i<k; ++i) {
            /*
            Incompatible due to function calls
            Ignored.
            */
            simpleNoDep0(new int[k]);
            a[i] = i;
        }
    }

    private static void functionCallNotIndexImpure1(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.printFun(i);
        }
    }

    private static void functionCallNotIndexImpure2(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.gvAccess(i);
        }
    }

    private static void functionCallNotIndexImpure3(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.gvModify(i);
        }
    }

    private static void functionCallNotIndexImpure4(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.arrAccess(i, new int[i+1]);
        }
    }

    private static void functionCallNotIndexImpure5(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.arrModify(i, i, new int[i+1]);
        }
    }

    private static void functionCallNotIndexImpure6(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.objAccess(new TestClass());
        }
    }

    private static void functionCallNotIndexImpure7(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.objModify(new TestClass(), i);
        }
    }

    private static void functionCallNotIndexImpure8(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.objMethodCall(new TestClass(), i);
        }
    }

    private static void functionCallNotIndexImpure9(int k) {
        for (int i=0; i<k; ++i) {
            TestCode.callImpureFun(i);
        }
    }

    private static void functionCallIndex(int k) {
        int[] a = new int[k];
        for (int i=0; i<k; ++i) {
            /*
            Incompatible due to function calls
            Ignored.
            */
            a[identity(i)] = i;
        }
    }

    public static void stackVariableDep1(int a, int b, int[] x, int[] y, int k) {
        for (int i=0; i<x.length; i++) {
            /*
            WAW Dependence on k
            Dependence found by Scalar Analysis
            */
            int j = a * b;
            y[a*i + b] = x[i];
            k = j;
        }
        System.out.println(k);
    }

    public static void multiDimArrayNoDep1(int a, int b, int[][] x, int[][] y) {
        for (int i=0; i<x.length; i++) {
            /*
            No Dependence.
            GeomPTA returns that x and y are not alias, because of main().
            Z3 Returns Status.Unsatisfiable
            */
            y[2*i][i] = x[i][2*i] + a*b;
        }
    }
    public static void multiDimArrayDep1(int a, int b, int[][] y) {
        for (int i=0; i<1000; i++) {
            /*
            RAW Dependence on y
            Dependence found by Z3.
            */
            y[2*i][i] = y[i][2*i] + a*b;
        }
    }

    public static void nestedNoDep1(int[] a) {
        for (int i=0; i < a.length; i++) {
            /*
            Generally, no dependence.
            Scalar analysis conservatively returns Not Parallelizable
            Issue: Scoping.
             */
            for (int j=0; j < a.length; j++) {
                /*
                No Dependence
                Z3 Returns Status.Unsatisfiable
                */
                a[j] = i * a[j];
            }
        }
    }

    public static void nestedDep1(int[] a) {
        for (int i=0; i < a.length; i++) {
            /*
            Generally, no dependence.
            Scalar analysis conservatively returns Not Parallelizable
            Issue: Scoping.
             */
            for (int j=0; j < a.length; j++) {
                /*
                RAW Dependence on y
                Dependence found by Z3.
                */
                a[i] = i * a[j];
            }
        }
    }

    /*
    The tests below have been taken from TornadoVM Examples:
    https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples
     */

    public static void saxpy(float alpha, float[] x, float[] y) {
        /*
        No Dependence
        Z3 Returns Status.Unsatisfiable
         */
        for (int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    public static void hilberComputationJava(float[] output, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            /*
            Generally, no dependence.
            Scalar analysis conservatively returns Not Parallelizable
            Issue: Scoping.
             */
            for (int j = 0; j < cols; j++) {
                /*
                No Dependence
                Z3 Returns Status.Unsatisfiable
                */
                output[i * rows + j] = (float) 1 / ((i + 1) + (j + 1) - 1);
            }
        }
    }

    public static void vectorAddition(int[] x, int[] y, int[] z) {
        for (int i = 0; i < y.length; i++) {
            /*
            No Dependence
            Z3 Returns Status.Unsatisfiable
            */
            z[i] = x[i] * y[i];
        }
    }

    private static void initializeVerticesNoDep(int[] vertices, int root) {
        int numNodes = vertices.length;
        for (int i = 0; i < numNodes; i++) {
            /*
            No Dependence
            Z3 Returns Status.Unsatisfiable
            */
            if (i == root) {
                vertices[i] = 0;
            } else {
                vertices[i] = -1;
            }
        }
    }

    private static short[] mandelbrot(int size) {
        final int iterations = 10000;
        float space = 2.0f / size;

        short[] result = new short[size * size];

        for (int i = 0; i < size; i++) {
            /*
            Scalar analysis conservatively returns Not Parallelizable
             */
            int indexIDX = i;
            for (int j = 0; j < size; j++) {
                /*
                Scalar analysis conservatively returns Not Parallelizable
                */
                int indexJDX = j;

                float Zr = 0.0f;
                float Zi = 0.0f;
                float Cr = (1 * indexJDX * space - 1.5f);
                float Ci = (1 * indexIDX * space - 1.0f);

                float ZrN = 0;
                float ZiN = 0;
                int y;

                for (y = 0; y < iterations && ZiN + ZrN <= 4.0f; y++) {
                    Zi = 2.0f * Zr * Zi + Ci;
                    Zr = 1 * ZrN - ZiN + Cr;
                    ZiN = Zi * Zi;
                    ZrN = Zr * Zr;
                }
                short r = (short) ((y * 255) / iterations);
                result[i * size + j] = r;
            }
        }
        return result;
    }

    private static void matrixMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
        for (int i = 0; i < size; i++) {
            /*
            Generally, no dependence.
            Scalar analysis conservatively returns Not Parallelizable
            Issue: Scoping.
            */
            for (int j = 0; j < size; j++) {
                /*
                Generally, no dependence.
                Scalar analysis conservatively returns Not Parallelizable
                Issue: Scoping.
                */
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    /*
                    RAW + WAR Dependence on sum
                    Dependence found by Scalar Dependence Analysis.
                    */
                    sum += A[(i * size) + k] * B[(k * size) + j];
                }
                C[(i * size) + j] = sum;
            }
        }
    }

    private static void computeMontecarlo(float[] output, final int iterations) {
        for (int j = 0; j < iterations; j++) {
            /*
            Incompatible due to function calls
            Rejected.
            */
            long seed = j;
            // generate a pseudo random number (you do need it twice)
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);

            // this generates a number between 0 and 1 (with an awful entropy)
            float x = (seed & 0x0FFFFFFF) / 268435455f;

            // repeat for y
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            float y = (seed & 0x0FFFFFFF) / 268435455f;

            float dist = (float) Math.sqrt(x * x + y * y);
            if (dist <= 1.0f) {
                output[j] = 1.0f;
            } else {
                output[j] = 0.0f;
            }
        }
    }

    private static float[] run2DConvolution(int nx, int ny, float[] a, float[] b) {
        float c11,c12,c13,c21,c22,c23,c31,c32,c33;

        c11 = +0.2f;  c21 = +0.5f;  c31 = -0.8f;
        c12 = -0.3f;  c22 = +0.6f;  c32 = -0.9f;
        c13 = +0.4f;  c23 = +0.7f;  c33 = +0.10f;

        for (int i = 1; i < nx - 1; i++) {
            /*
            Generally, no dependence.
            Scalar analysis conservatively returns Not Parallelizable
            Issue: Scoping.
            */
            for (int j = 1; j < ny - 1; j++) {
                /*
                No Dependence
                Z3 Returns Status.Unsatisfiable
                */
                b[i * nx + j] = c11 * a[(i - 1) * nx + (j - 1)] + c21 * a[(i - 1) * nx + (j + 0)] + c31 * a[(i - 1) * nx + (j + 1)] + c12 * a[(i + 0) * nx + (j - 1)] + c22 * a[(i + 0) * nx + (j + 0)]
                        + c32 * a[(i + 0) * nx + (j + 1)] + c13 * a[(i + 1) * nx + (j - 1)] + c23 * a[(i + 1) * nx + (j + 0)] + c33 * a[(i + 1) * nx + (j + 1)];
            }
        }
        return b;
    }

    public static void fdtd(int nx, int ny, float[] fict, float[] ey, float[] hz, int step) {
        for (int i = 0; i < nx; i++) {
            /*
            Generally, no dependence.
            Scalar analysis conservatively returns Not Parallelizable
            Issue: Scoping.
            */
            for (int j = 0; j < ny; j++) {
                /*
                Scalar analysis conservatively returns Not Parallelizable
                */
                if (i == 0) {
                    ey[0 * nx + j] = fict[step];
                } else {
                    ey[i * nx + j] = (float) (ey[i * nx + j] - 0.5 * (hz[i * nx + j] - hz[(i - 1) * nx + j]));
                }
            }
        }
    }

    public static short[][] multiMandelbrot(int n) {
        short[][] ar = new short[n+1][];
        for (int i=1; i<=n; ++i) {
            ar[i] = mandelbrot(i);
        }
        return ar;
    }

    public static BinarySearchTree[] getTrees(int n) {
        BinarySearchTree[] binarySearchTrees = new BinarySearchTree[n];
        for(int i=0; i<n; ++i) {
            BinarySearchTree.create(i+1);
        }
        return binarySearchTrees;
    }

}