import soot.Local;
import soot.Unit;

public class ArrayReference {
    public Local index;
    public Unit unit;
    public ArrayReference(Local i, Unit u) {
        index = i;
        unit = u;
    }
}
