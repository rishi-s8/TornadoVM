public class TestClass {
    public int field1 = 10;
    public TestClass tc;

    public void modifyField(int f) {
        this.field1 = f;
    }

    public int returnField() {
        return this.field1;
    }
}