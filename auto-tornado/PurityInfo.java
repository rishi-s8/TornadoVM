import soot.SootMethod;
import soot.Unit;

import java.util.HashSet;
import java.util.Set;

public class PurityInfo {
    private SootMethod analysedMethod;
    private Set<Unit> readImpure;
    private Set<Unit> writeImpure;

    public PurityInfo(SootMethod subjectMethod) {
        this(subjectMethod, null, null);
    }

    public PurityInfo(SootMethod subjectMethod, Set<Unit> readImpure, Set<Unit> writeImpure) {
        this.analysedMethod = subjectMethod;
        if (readImpure == null) this.readImpure = new HashSet<>();
        else this.readImpure = new HashSet<>(readImpure);
        if (writeImpure == null) this.writeImpure = new HashSet<>();
        else this.writeImpure = new HashSet<>(writeImpure);
    }

    public void addReadImpureUnit(Unit u) {
        readImpure.add(u);
    }

    public void addWriteImpureUnit(Unit u) {
        writeImpure.add(u);
    }

    public boolean isReadPure() {
        return readImpure.isEmpty();
    }

    public boolean isWritePure() {
        return writeImpure.isEmpty();
    }

    public Set<Unit> getReadImpureUnits() {
        return readImpure;
    }

    public Set<Unit> getWriteImpureUnits() {
        return writeImpure;
    }

    public SootMethod getMethod() {
        return analysedMethod;
    }

    @Override
    public String toString() {
        return "Read Pure = " + this.isReadPure() +
                "\nWrite Pure = " + this.isWritePure();
    }
}
