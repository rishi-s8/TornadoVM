/**
 * As part of PA1, CS611: Program Analysis
 * Provided by, Dr. Manas Thakur, IIT Mandi
 */

import java.util.*;

import soot.Local;
import soot.Unit;
import soot.ValueBox;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ArraySparseSet;

class LiveVariableAnalysis extends BackwardFlowAnalysis<Unit, FlowSet<Local>> {
    private FlowSet<Local> emptySet;

    public LiveVariableAnalysis(DirectedGraph g) {
        super(g);
        emptySet = new ArraySparseSet<Local>();
        doAnalysis();
    }

    /**
     * Used to initialize the in and out sets for each node
     * In our case we build up the sets as we go, so we initialize
     * with the empty set.
     */
    @Override
    protected FlowSet<Local> newInitialFlow() {
        return emptySet.clone();
    }

    /**
     * Returns FlowSet representing the initial set of the entry node
     * In our case the entry node is the last node and it
     * should contain the empty set.
     */
    @Override
    protected FlowSet<Local> entryInitialFlow() {
        return emptySet.clone();
    }

    /**
     * Standard copy routine
     */
    @Override
    protected void copy(FlowSet<Local> src, FlowSet<Local> dst) {
        src.copy(dst);
    }

    /**
     * Perform a join operation over successor nodes (backward analysis)
     * As live variables is a may analysis we join by union
     */
    @Override
    protected void merge(FlowSet<Local> in1, FlowSet<Local> in2, FlowSet<Local> out) {
        in1.union(in2, out);
    }

    /**
     * Set the out (entry) based on the in (exit)
     */
    @Override
    protected void flowThrough(FlowSet<Local> in, Unit node, FlowSet<Local> out) {
        FlowSet defs = (FlowSet) emptySet.clone();
        for (ValueBox def : node.getDefBoxes()) {
            if (def.getValue() instanceof Local) {
                defs.add(def.getValue());
            }
        }

        in.difference(defs, out);

        for (ValueBox use : node.getUseBoxes()) {
            if (use.getValue() instanceof Local) {
                out.add((Local) use.getValue());
            }
        }
    }
}
