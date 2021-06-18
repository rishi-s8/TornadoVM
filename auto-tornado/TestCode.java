public class TestCode {

    public static int globalvar1 = 50;
    public static boolean globalvar2;

    public static void main(String[] args) {

        int var1 = 100;
        globalvar2 = false;
        int[] arr1 = new int[10];
        arr1[0] = 5;
        TestClass obj1 = new TestClass();

        printFun(var1);
        int a = pureFun(var1);
        int b = gvAccess(var1);
        gvModify(var1);
        int c = arrAccess(1, arr1);
        arrModify(var1, 0, arr1);
        int d = objAccess(obj1);
        objModify(obj1, var1);
        objMethodCall(obj1, var1);
        int e = callPureFun(var1);
        int f = callImpureFun(var1);
    }

    // Function performing I/O operation
    static void printFun(int val) {
        int a = 2;
        int b = val + a;
        System.out.println(b);
    }

    // A pure function
    static int pureFun(int val) {
        int a = 2;
        int b = a + val;
        return b;
    }

    // Function reading a global variable
    static int gvAccess(int val) {
        int a = 2;
        int b = a + val + globalvar1;
        return b;
    }

    // Function writing to a global variable
    static void gvModify(int val) {
        int a = 2;
        int b = a + val;
        globalvar1 = b;
    }

    // Function reading an array
    static int arrAccess(int index, int[] arr) {
        return arr[index];
    }

    // Function writing to an array
    static void arrModify(int val, int index, int[] arr) {
        int a = 2;
        arr[index] = a + val;
    }

    // Function reading an object's field
    static int objAccess(TestClass obj) {
        int a = 2;
        return obj.field1 + a;
    }

    // Function writing to an object's field
    static void objModify(TestClass obj, int val) {
        obj.field1 = val;
    }

    // Function invoking object's methods
    static void objMethodCall(TestClass obj, int val) {
        int a = 2;
        int b = a + obj.returnField();
        obj.modifyField(b);
    }

    // A pure function calling another pure function
    static int callPureFun(int val) {
        int a = 2;
        int b = val + a;
        int c = pureFun(b) + val;
        return c;
    }

    // A pure function calling an impure function
    static int callImpureFun(int val) {
        int a = 2;
        int b = val + a;
        int c = gvAccess(b) + val;
        return c;
    }
}