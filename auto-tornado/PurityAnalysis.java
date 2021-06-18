import soot.*;
import soot.jimple.*;
import soot.jimple.spark.geom.geomPA.GeomPointsTo;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;
import java.util.List;
import java.util.Iterator;

import java.util.*;

class PurityAnalysis {
    private final boolean verbose;
    private final CallGraph cg;
    private final GeomPointsTo pta;

    private final Set<SootMethod> visitedMethods;
    private final List<SootMethod> methodTopologicalSortedList;
    private final Map<SootMethod, PurityInfo> methodPurityInfoMap;

    public PurityAnalysis() {
        this(false);
    }

    public PurityAnalysis(boolean verbose) {
        this.verbose = verbose;
        methodTopologicalSortedList = new ArrayList<>();
        methodPurityInfoMap = new HashMap<>();
        visitedMethods = new HashSet<>();
        cg = Scene.v().getCallGraph();
        pta = (GeomPointsTo) Scene.v().getPointsToAnalysis();

        verbosePrint("Verbose Mode: True\n--------------");

        interproceduralAnalysis();
    }

    public PurityInfo getPurityInfo(SootMethod method) {
        return methodPurityInfoMap.get(method);
    }

    private void verbosePrint(String message) {
        if (verbose) System.out.println(message);
    }

    private void topologicalSort(SootMethod method) {
        visitedMethods.add(method);
        Body body = method.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        for (Unit u : units) {
            if (((Stmt) u).containsInvokeExpr()) {
                Iterator<Edge> it = cg.edgesOutOf(u);
                while (it.hasNext()) {
                    SootMethod nextMethod = it.next().tgt();
                    if (!(nextMethod.isJavaLibraryMethod() || nextMethod.isStaticInitializer() || nextMethod.isConstructor()))
                        if (!(visitedMethods.contains(nextMethod))) {
                            topologicalSort(nextMethod);
                        }
                }
            }
        }
        methodTopologicalSortedList.add(method);
    }

    private void interproceduralAnalysis() {
        verbosePrint("\nStarting interproceduralAnalysis()");

        SootMethod mainMethod = Scene.v().getEntryPoints().get(0);
        verbosePrint("mainMethod: ".concat(mainMethod.getName()));

        verbosePrint("\nStarting topologicalSort()");
        topologicalSort(mainMethod);
        verbosePrint("Completed topologicalSort()");

        for (SootMethod method : methodTopologicalSortedList)
            intraproceduralAnalysis(method);
    }

    private Set<Local> getExtPointingLocals(Body body) {
        verbosePrint("Starting getExtPointingLocals()");

        Chain<Unit> units = body.getUnits();
        Set<Local> extPointingLocals = new HashSet<>(body.getParameterLocals());
        Set<Local> locals = new HashSet<>(body.getLocals());
        locals.removeAll(extPointingLocals);
        extPointingLocals.removeIf(p -> (p.getType() instanceof PrimType));

        boolean continueLoop = true;

        while (continueLoop) {
            continueLoop = false;

            Set<Local> toTransfer = new HashSet<>();
            for (Local p : extPointingLocals)
                for (Local l : locals)
                    if (pta.reachingObjects(p).hasNonEmptyIntersection(pta.reachingObjects(l))) {
                        toTransfer.add(l);
                    }

            extPointingLocals.addAll(toTransfer);
            locals.removeAll(toTransfer);

            for (Unit u : units) {
                if (u instanceof IdentityStmt) {
                    if (((IdentityStmt) u).getRightOp() instanceof ThisRef) {
                        extPointingLocals.add((Local) ((IdentityStmt) u).getLeftOp());
                    }
                }
                if (u instanceof AssignStmt) {
                    AssignStmt as = (AssignStmt) u;
                    if (as.getLeftOp() instanceof Local && locals.contains((Local) as.getLeftOp())) {
                        if (as.getRightOp() instanceof Local && extPointingLocals.contains((Local) as.getRightOp())) {
                            extPointingLocals.add((Local) as.getLeftOp());
                            continueLoop = true;
                        } else if (as.getRightOp() instanceof InstanceFieldRef) {
                            InstanceFieldRef ifr = (InstanceFieldRef) as.getRightOp();
                            if (!(ifr.getField().getType() instanceof PrimType) && extPointingLocals.contains((Local) ifr.getBase())) {
                                extPointingLocals.add((Local) as.getLeftOp());
                                continueLoop = true;
                            }
                        }
                    }
                }
            }
            locals.removeAll(extPointingLocals);
        }

        verbosePrint("Completed getExtPointingLocals()");
        return extPointingLocals;
    }


    private void intraproceduralAnalysis(SootMethod method) {
        verbosePrint("\nStarting intraproceduralAnalysis() for the method: ".concat(method.getName()));
        verbosePrint("------------------------------------------------------");

        PurityInfo purityInfo = new PurityInfo(method);
        Body body = method.getActiveBody();
        Chain<Unit> units = body.getUnits();
        Set<Local> extPointingLocals = getExtPointingLocals(body);

        for (Unit u : units) {
//            System.out.println(u);
            List<ValueBox> useBoxes = u.getUseBoxes();

            if (((Stmt) u).containsInvokeExpr()) {
                boolean readImpure = false, writeImpure = false;
                Iterator<Edge> it = cg.edgesOutOf(u);
                while (it.hasNext()) {
                    SootMethod nextMethod = it.next().tgt();
                    if (methodPurityInfoMap.containsKey(nextMethod)) {
                        readImpure = readImpure || !methodPurityInfoMap.get(nextMethod).isReadPure();
                        writeImpure = writeImpure || !methodPurityInfoMap.get(nextMethod).isWritePure();
                    }
                }
                if (readImpure) {
                    verbosePrint("Adding to READ impure: ".concat(u.toString()));
                    purityInfo.addReadImpureUnit(u);
                }
                if (writeImpure) {
                    verbosePrint("Adding to WRITE impure: ".concat(u.toString()));
                    purityInfo.addWriteImpureUnit(u);
                }
            }
            if (u instanceof AssignStmt) {
                AssignStmt as = (AssignStmt) u;
                useBoxes = as.getRightOp().getUseBoxes();
                if (as.getLeftOp() instanceof InstanceFieldRef) {
                    InstanceFieldRef ifr = (InstanceFieldRef) as.getLeftOp();
                    if (extPointingLocals.contains((Local) ifr.getBase())) {
                        verbosePrint("Adding to WRITE impure: ".concat(u.toString()));
                        purityInfo.addWriteImpureUnit(u);
                    }
                } else if (as.getLeftOp() instanceof ArrayRef) {
                    ArrayRef ar = (ArrayRef) as.getLeftOp();
                    if (extPointingLocals.contains((Local) ar.getBase())) {
                        verbosePrint("Adding to WRITE impure: ".concat(u.toString()));
                        purityInfo.addWriteImpureUnit(u);
                    }
                } else if (as.getLeftOp() instanceof StaticFieldRef) {
                    verbosePrint("Adding to WRITE impure: ".concat(u.toString()));
                    purityInfo.addWriteImpureUnit(u);
                }

                if (as.getRightOp() instanceof Local && extPointingLocals.contains((Local) as.getRightOp())) {
                    verbosePrint("Adding to READ impure: ".concat(u.toString()));
                    purityInfo.addReadImpureUnit(u);
                } else if (as.getRightOp() instanceof StaticFieldRef) {
                    verbosePrint("Adding to READ impure: ".concat(u.toString()));
                    purityInfo.addReadImpureUnit(u);
                }
            }

            for (ValueBox ub : useBoxes) {
                if (ub.getValue() instanceof Local && extPointingLocals.contains((Local) ub.getValue())) {
                    if (!(u instanceof AssignStmt) || !(((AssignStmt) u).getRightOp() instanceof InterfaceInvokeExpr)) {
                        verbosePrint("Adding to READ impure: ".concat(u.toString()));
                        purityInfo.addReadImpureUnit(u);
                    }
                }
            }
        }

        methodPurityInfoMap.put(method, purityInfo);

        verbosePrint("Completed intraproceduralAnalysis for the method: ".concat(method.getName()));
        verbosePrint("\nResults for the method ".concat(method.getName()).concat(" are\n").concat(purityInfo.toString()));
    }
}
