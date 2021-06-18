import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Iterator;

import com.microsoft.z3.Context;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.spark.geom.geomPA.GeomPointsTo;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.jimple.toolkits.annotation.logic.LoopFinder;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.scalar.ConstantPropagatorAndFolder;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.SimpleLocalDefs;
import uk.ac.manchester.tornado.annotation.AnnotationMap;
import com.microsoft.z3.*;

public class RunLoopPar {
    public static void main(String[] args) {
        String classPath = ".:" + args[0];

        String[] sootArgs = { "-cp", classPath, "-pp", "-w", "-f", "J", "-keep-line-number", "-keep-bytecode-offset" , "-no-bodies-for-excluded",
                "-p", "jb", "use-original-names",
                "-p", "cg", "enabled:true",
                "-p", "cg.spark", "enabled:true",
                "-p", "cg.spark", "geom-app-only:false",
                "-p", "cg.spark", "simplify-offline:false",
                "-p", "cg.spark", "propagator:worklist",
                "-p", "cg.spark", "on-fly-cg:true",
                "-p", "cg.spark", "set-impl:double",
                "-p", "cg.spark", "double-set-old:hybrid",
                "-p", "cg.spark", "double-set-new:hybrid",
                "-p", "cg.spark", "geom-pta:true",
                "-p", "cg.spark", "geom-runs:2",
                "-main-class", args[1], args[1], "-O" };
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.pea", new LoopPar()));
        soot.Main.main(sootArgs);
    }

}

class LoopPar extends SceneTransformer {
    static ConstantPropagatorAndFolder c = ConstantPropagatorAndFolder.v();
    static LiveVariableAnalysis liveVariableAnalysis;
    static LocalDefs ld;
    static UnitGraph graph;
    static AnnotationMap annotationMap = new AnnotationMap();
    static HashSet<Local> loopVars;
    static PurityAnalysis purityAnalysis;
    static CallGraph cg;

    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {
        System.out.println("Starting analysis. MainClass: " + Scene.v().getMainClass());
        cg = Scene.v().getCallGraph();
        purityAnalysis = new PurityAnalysis(true);

        for (SootMethod mainMethod : Scene.v().getMainClass().getMethods()) {
            if (!(mainMethod.isStaticInitializer() || mainMethod.isPhantom() || mainMethod.isConstructor() || mainMethod.isJavaLibraryMethod()))
                processCFG(mainMethod);
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream("annotationMap");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            assert oos != null;
            oos.writeObject(annotationMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static Stmt correctHead(Stmt head, List<Stmt> statements) {
        for (Stmt s : statements) {
            if (s instanceof JIfStmt) {
                head = s;
                break;
            }
        }
        return head;
    }

    protected static boolean pathWithoutVisitingANode(Unit from, Unit to, Unit notVisit, HashSet<Unit> visited) {
        if (from == to) {
            return true;
        }
        visited.add(from);

        if (from == notVisit) {
            return false;
        }

        for (Unit successor: graph.getSuccsOf(from)) {
            if (!visited.contains(successor)) {
                if (pathWithoutVisitingANode(successor, to, notVisit, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    protected static boolean interIterationPath(JAssignStmt assignStmt, Unit def, Stmt updateStmt, Stmt head) {
        return assignStmt == def || (pathWithoutVisitingANode(def, updateStmt, assignStmt, new HashSet<>()) &&
                pathWithoutVisitingANode(head, assignStmt, def, new HashSet<>()));
    }


    protected static boolean definedInLoop(JAssignStmt assignStmt, Stmt head, Stmt updateStmt) {
        if (loopVars.contains((Local) assignStmt.leftBox.getValue())) { // Local is index of inner loop
            System.out.println("loopVars contains " + assignStmt.leftBox.getValue());
            return false;
        }
        for (ValueBox vb: assignStmt.getUseBoxes()) {
            if (vb.getValue() instanceof Local) {
                if (loopVars.contains((Local) vb.getValue())) { // Local is index of inner loop
                    continue;
                }
                List<Unit> defs = ld.getDefsOfAt(((Local) vb.getValue()), assignStmt);
                for (Unit def: defs) {
                    if (!(def instanceof JIdentityStmt) && def != updateStmt) {
                        if (interIterationPath(assignStmt, def, updateStmt, head)) {
                            return true;
                        } else {
                            definedInLoop(((JAssignStmt) def), head, updateStmt);
                        }
                    }
                }
            }
        }
        return false;
    }

    protected static void removeNonLocalVars(HashSet<Local> localVars, Loop l, Stmt updateStmt) {
        Stmt head = correctHead(l.getHead(), l.getLoopStatements());
        if (l.targetsOfLoopExit(head).size() > 0) {
            FlowSet<Local> liveVars = liveVariableAnalysis.getFlowBefore(
                    ((Unit) l.targetsOfLoopExit(head).toArray()[0])
                    );
            for (Local local: liveVars) {
                localVars.remove(local);
            }
        }

        for (Stmt stmt: l.getLoopStatements()) { // This can be optimized. Top down order. Memoization.
            if ((stmt instanceof JAssignStmt)) {
                JAssignStmt jas = (JAssignStmt) stmt;
                if ((jas.leftBox.getValue() instanceof Local) && (definedInLoop(jas, l.getHead(), updateStmt))) {
                    localVars.remove((Local) jas.leftBox.getValue());
                }
            }
        }
    }

    protected static boolean addArrayRef(HashMap<Local, ArrayList<ArrayReference>> arrayRefs, Stmt stmt, Value arrayVar, Value indexVar) {
        if (arrayVar instanceof Local && indexVar instanceof Local) {
            Local local = (Local) arrayVar;
            ArrayList<ArrayReference> tempArrayWrites;
            if (arrayRefs.containsKey(local)) {
                tempArrayWrites = arrayRefs.get(local);
            } else {
                tempArrayWrites = new ArrayList<>();
                arrayRefs.put(local, tempArrayWrites);
            }
            tempArrayWrites.add(new ArrayReference((Local) indexVar, stmt));
        } else {
            return false;
        }
        return true;
    }

    protected static boolean canAlias(Local a, Local b) {
        GeomPointsTo pointsToAnalysis = (GeomPointsTo) Scene.v().getPointsToAnalysis();
        PointsToSet s1 = pointsToAnalysis.reachingObjects(a);
        PointsToSet s2 = pointsToAnalysis.reachingObjects(b);
        return s1.hasNonEmptyIntersection(s2);
    }

    protected static boolean checkFunctionCallCompatibility(Loop l) {
        // Functions not supported as of now
        System.out.println("Checking for FunctionCompatibility");
        for (Stmt stmt: l.getLoopStatements()) {
            if (stmt.containsInvokeExpr()) {
                Iterator<Edge> it = cg.edgesOutOf(stmt);
                System.out.println("Here!");
                while (it.hasNext()) {
                    SootMethod nextMethod = it.next().tgt();
                    System.out.println("isJavaLibrary: " + nextMethod.isJavaLibraryMethod());
                    System.out.println("IsPhantom: " + nextMethod.isPhantom());
                    System.out.println("IsSI: " + nextMethod.isStaticInitializer());
                    System.out.println("IsConstructor: " + nextMethod.isConstructor());
                    if (!(nextMethod.isJavaLibraryMethod() || nextMethod.isStaticInitializer() || nextMethod.isConstructor() || nextMethod.isPhantom())) {
                        PurityInfo pi = purityAnalysis.getPurityInfo(nextMethod);
                        System.out.println("ReadPure: " + pi.isReadPure());
                        System.out.println("WritePure: " + pi.isWritePure());
                        if (!pi.isReadPure() || !pi.isWritePure()) {
                            return false;
                        }
                    }
                    else {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected static String getVarName(Local l, String suffix, HashSet<Local> localVars, Local var) {
        if (localVars.contains(l) || var == l)
            return l.getName() + "_" + suffix;
        else
            return l.getName();
    }

    protected static IntExpr createNewVar(String varName, HashMap<String, IntExpr> z3Vars, Context ctx) {
        IntExpr temp = ctx.mkIntConst(varName);
        z3Vars.put(varName, temp);
        return temp;
    }

    protected static IntExpr getVar(String varName, HashMap<String, IntExpr> z3Vars, Context ctx) {
        if (z3Vars.containsKey(varName)) {
            return z3Vars.get(varName);
        } else {
            return createNewVar(varName, z3Vars, ctx);
        }
    }

    protected static BoolExpr constructIndexLogic(Local l, Stmt stmt, Local var, HashSet<Local> localVars,
                                                  HashMap<String, IntExpr> z3Vars, Context ctx,
                                                  String suffix, HashSet<Local> nonLocalDefVisited,
                                                  HashSet<Local> localDefVisited) {
        BoolExpr constraints = ctx.mkTrue();

        if (l == var) {
            return constraints;
        }

        for (Unit def: ld.getDefsOfAt(l, stmt)) {
            if (((DefinitionStmt) def).getRightOp() instanceof IdentityRef ||
                    ((DefinitionStmt) def).getRightOp() instanceof Constant ||
                    nonLocalDefVisited.contains(l) || localDefVisited.contains(l)) {
                return constraints;
            }
        }

        BoolExpr defConstraints = ctx.mkFalse();
        for (Unit def: ld.getDefsOfAt(l, stmt)) {
            JAssignStmt assignStmt = (JAssignStmt) def;
            Value rhs = assignStmt.getRightOp();
            String lhsName = getVarName(l, suffix, localVars, var);
            IntExpr lhsExpr = getVar(lhsName, z3Vars, ctx);
            if (rhs instanceof IntConstant) {
                IntExpr rhsExpr = ctx.mkInt(((IntConstant) rhs).value);
                defConstraints = ctx.mkOr(defConstraints, ctx.mkEq(lhsExpr, rhsExpr));
            } else if (rhs instanceof Local) {
                String opName = getVarName((Local) rhs, suffix, localVars, var);
                IntExpr opExpr = getVar(opName, z3Vars, ctx);
                defConstraints = ctx.mkOr(defConstraints, ctx.mkEq(lhsExpr, opExpr));
            } else if (rhs instanceof BinopExpr) {
                Value op1 = ((BinopExpr) rhs).getOp1();
                Value op2 = ((BinopExpr) rhs).getOp2();
                IntExpr op1Expr, op2Expr;
                if (op1 instanceof IntConstant && op2 instanceof IntConstant) {
                    op1Expr = ctx.mkInt(((IntConstant) op1).value);
                    op2Expr = ctx.mkInt(((IntConstant) op2).value);
                } else if (op1 instanceof IntConstant && op2 instanceof Local) {
                    op1Expr = ctx.mkInt(((IntConstant) op1).value);
                    String op2Name = getVarName((Local) op2, suffix, localVars, var);
                    op2Expr = getVar(op2Name, z3Vars, ctx);
                } else if (op2 instanceof IntConstant && op1 instanceof Local) {
                    op2Expr = ctx.mkInt(((IntConstant) op2).value);
                    String op1Name = getVarName((Local) op1, suffix, localVars, var);
                    op1Expr = getVar(op1Name, z3Vars, ctx);
                } else if (op1 instanceof Local && op2 instanceof Local) {
                    String op1Name = getVarName((Local) op1, suffix, localVars, var);
                    op1Expr = getVar(op1Name, z3Vars, ctx);
                    String op2Name = getVarName((Local) op2, suffix, localVars, var);
                    op2Expr = getVar(op2Name, z3Vars, ctx);
                } else {
                    return null;
                }

                if (rhs instanceof JAddExpr) {
                    ArithExpr<IntSort> arithExpr = ctx.mkAdd(op1Expr, op2Expr);
                    defConstraints = ctx.mkOr(defConstraints, ctx.mkEq(lhsExpr, arithExpr));
                } else if (rhs instanceof JSubExpr) {
                    ArithExpr<IntSort> arithExpr = ctx.mkSub(op1Expr, op2Expr);
                    defConstraints = ctx.mkOr(defConstraints, ctx.mkEq(lhsExpr, arithExpr));
                } else if (rhs instanceof JMulExpr) {
                    ArithExpr<IntSort> arithExpr = ctx.mkMul(op1Expr, op2Expr);
                    defConstraints = ctx.mkOr(defConstraints, ctx.mkEq(lhsExpr, arithExpr));
                } else {
                    return null;
                }
            }
        }
        constraints = ctx.mkAnd(constraints, defConstraints);

        if (!localVars.contains(l)) {
            nonLocalDefVisited.add(l);
        } else {
            localDefVisited.add(l);
        }

        for (Unit def: ld.getDefsOfAt(l, stmt)) {
            for (ValueBox vb: def.getUseBoxes()) {
                if (vb.getValue() instanceof Local) {
                    BoolExpr c = constructIndexLogic((Local) vb.getValue(),(Stmt) def, var, localVars,
                            z3Vars, ctx, suffix, nonLocalDefVisited, localDefVisited);
                    if (c != null) {
                        constraints = ctx.mkAnd(constraints, c);
                    } else {
                        return null;
                    }
                }
            }
        }

        return constraints;
    }

    protected static BoolExpr getIterationConstraints(ArrayReference arrayReference, String suffix,
                                                      HashSet<Local> localVars, Local var,
                                                      HashMap<String, IntExpr> z3Vars, Context ctx, IntConstant lb,
                                                      Value ub, HashSet<Local> nonLocalDefsVisited) {
        BoolExpr constraints = ctx.mkTrue();
        String name = getVarName(arrayReference.index, suffix, localVars, var);
        createNewVar(name, z3Vars, ctx);

        IntExpr lowerBound = ctx.mkInt(lb.value);

        String varName = getVarName(var, suffix, localVars, var);
        IntExpr varExpr = createNewVar(varName, z3Vars, ctx);
        BoolExpr varConstraint = ctx.mkGe(varExpr, lowerBound);
        if (ub instanceof IntConstant) {
            varConstraint = ctx.mkAnd(ctx.mkLe(varExpr, ctx.mkInt(((IntConstant) ub).value)));
        }

        constraints = ctx.mkAnd(constraints, varConstraint);

        BoolExpr indexLogic = constructIndexLogic(arrayReference.index, (Stmt) arrayReference.unit, var, localVars,z3Vars, ctx,
                suffix, nonLocalDefsVisited, new HashSet<>());

        if (indexLogic != null) {
            constraints = ctx.mkAnd(constraints, indexLogic);
        } else {
            return null;
        }
        return constraints;
    }

    protected static BoolExpr separateIterations(String name1, String name2, String varName1, String varName2,
                                                 HashMap<String, IntExpr> z3Vars, Context ctx) {
        IntExpr varExpr1 = z3Vars.get(varName1);
        IntExpr varExpr2 = z3Vars.get(varName2);
        BoolExpr varNotEq = ctx.mkNot(ctx.mkEq(varExpr1, varExpr2));

        IntExpr indexExpr1 = z3Vars.get(name1);
        IntExpr indexExpr2 = z3Vars.get(name2);
        BoolExpr indexEqual = ctx.mkEq(indexExpr1, indexExpr2);

        return ctx.mkAnd(varNotEq, indexEqual);
    }

    protected static BoolExpr getAllConstraints(ArrayReference arrayReference1, ArrayReference arrayReference2,
                                                int i, int j, HashSet<Local> localVars,
                                                HashSet<Local> nonLocalDefsVisited, HashMap<String, IntExpr> z3Vars,
                                                Context ctx, IntConstant lb, Value ub, Local var) {
        BoolExpr constraints = ctx.mkTrue();
        String suffix1 = i + "_" + j + "_1";
        String suffix2 = i + "_" + j + "_2";
        BoolExpr c1 = getIterationConstraints(arrayReference1, suffix1, localVars, var, z3Vars,
                ctx, lb, ub, nonLocalDefsVisited);
        BoolExpr c2 = getIterationConstraints(arrayReference2, suffix2, localVars, var, z3Vars,
                ctx, lb, ub, nonLocalDefsVisited);

        if (c1 != null && c2 != null) {
            constraints = ctx.mkAnd(constraints, c1, c2);
        } else {
            return null;
        }

        String indexName1 = getVarName(arrayReference1.index, suffix1, localVars, var);
        String indexName2 = getVarName(arrayReference2.index, suffix2, localVars, var);
        String varName1 = getVarName(var, suffix1, localVars, var);
        String varName2 = getVarName(var, suffix2, localVars, var);

        return ctx.mkAnd(constraints, separateIterations(indexName1, indexName2,
                varName1, varName2, z3Vars, ctx));
    }

    protected static boolean arrayDependence(HashMap<Local, ArrayList<ArrayReference>> arrayWrites,
                                             HashMap<Local, ArrayList<ArrayReference>> arrayReads,
                                             IntConstant lb, Value ub, Local var, HashSet<Local> localVars) {
        System.out.println("Adding Array Constraints");
        HashMap<String, IntExpr> z3Vars = new HashMap<>();
        HashSet<Local> nonLocalDefsVisited = new HashSet<>();
        Context ctx = new Context();
        BoolExpr constraints = ctx.mkFalse();
        int i = 0;
        for (Local base1: arrayWrites.keySet()) {
            for (ArrayReference arrayReference1: arrayWrites.get(base1)) {
                int j = 0;
                for (Local base2: arrayWrites.keySet()) {
                    if (canAlias(base1, base2)) {
                        for (ArrayReference arrayReference2 : arrayWrites.get(base2)) {
                            BoolExpr c = getAllConstraints(arrayReference1, arrayReference2, i, j, localVars,
                                    nonLocalDefsVisited, z3Vars, ctx, lb, ub, var);
                            if (c != null) {
                                constraints = ctx.mkOr(constraints, c);
                            } else {
                                System.out.println("Unsupported array indices");
                                return false;
                            }
                            j++;
                        }
                    }
                }
                for (Local base2: arrayReads.keySet()) {
                    if (canAlias(base1, base2)) {
                        for (ArrayReference arrayReference2 : arrayReads.get(base2)) {
                            BoolExpr c = getAllConstraints(arrayReference1, arrayReference2, i, j, localVars,
                                    nonLocalDefsVisited, z3Vars, ctx, lb, ub, var);
                            if (c != null) {
                                constraints = ctx.mkOr(constraints, c);
                            } else {
                                System.out.println("Unsupported array indices");
                                return false;
                            }
                            j++;
                        }
                    }
                }
                i++;
            }
        }

        Solver z3Solver = ctx.mkSolver();
        z3Solver.add(constraints);

        System.out.println("Adding Constraints Complete. Initiating solver.");
        // System.out.println(constraints);
        Status q = z3Solver.check();
        System.out.println("Solver Completed: " + q);
        return q == Status.UNSATISFIABLE;
    }

    protected static void analyseLoop(Loop l, Body body, String signature) {

        PatchingChain<Unit> units = body.getUnits();

        List<Stmt> statements = l.getLoopStatements();
        Stmt head = correctHead(l.getHead(), l.getLoopStatements());

        Stmt backJumpStatement = l.getBackJumpStmt();
        Stmt updateStatement = statements.get(statements.size()-2); // must be atleast 2: head, back jump
        Value var, inc, lb, ub;

        // BackJumpStatement at the end?
        if (backJumpStatement != statements.get(statements.size()-1)) {
            System.out.println("Improper BackJump statement.");
            System.out.println("NOT Parallelizable");
            return;
        }

        // updateStatement assignment?
        if (!(updateStatement instanceof JAssignStmt)) {
            System.out.println("Update Statement is not Assignment");
            System.out.println("NOT Parallelizable");
            return;
        }

        var = updateStatement.getDefBoxes().get(0).getValue();

        // Check if var is Local

        if (!(var instanceof Local)) {
            System.out.println("var (" + var + ") not Local!");
            System.out.println("NOT Parallelizable");
            return;
        }

        // check if this var appears in the head
        boolean found = false;
        for (ValueBox v : head.getUseBoxes()) {
            if (v.getValue() == var) {
                found = true;
                break;
            }
        }

        if (!found) {
            System.out.println("var (" + var + ") not in head: " + head);
            System.out.println("NOT Parallelizable");
            return;
        }

        // Check if != operator is supported by TornadoVM
        if ((head.getUseBoxes().get(2).getValue() instanceof JGeExpr
                || head.getUseBoxes().get(2).getValue() instanceof JGtExpr)
                && head.getUseBoxes().get(0).getValue() == var) {
            ub = head.getUseBoxes().get(1).getValue();
        } else if ((head.getUseBoxes().get(2).getValue() instanceof JLeExpr
                || head.getUseBoxes().get(2).getValue() instanceof JLtExpr)
                && head.getUseBoxes().get(1).getValue() == var) {
            ub = head.getUseBoxes().get(0).getValue();
        } else {
            System.out.println("Unsupported Condition");
            System.out.println("NOT Parallelizable");
            return;
        }

        if (!(updateStatement.getUseBoxes().get(0).getValue() instanceof JAddExpr)) {
            System.out.println("RHS(updateStmt) isn't AddExpr");
            System.out.println("NOT Parallelizable");
            return;
        }

        if (updateStatement.getUseBoxes().get(1).getValue() == var) {
            inc = updateStatement.getUseBoxes().get(2).getValue();
        } else if (updateStatement.getUseBoxes().get(2).getValue() == var) {
            inc = updateStatement.getUseBoxes().get(1).getValue();
        } else {
            System.out.println("Update RHS doesn't have var");
            System.out.println("NOT Parallelizable");
            return;
        }

        // var not in defBox
        for (int i=0; i<statements.size()-2; i++) { // Last two are updateStmt and goto
            for (ValueBox v : statements.get(i).getDefBoxes()) {
                if (v.getValue() == var) {
                    System.out.println("Var being assigned inside the loop");
                    System.out.println("NOT Parallelizable");
                    return;
                }
            }
        }

        // No breaks : single exit statement
        if (!l.hasSingleExit()) {
            System.out.println("Multiple Exits");
            System.out.println("NOT Parallelizable");
            return;
        }

        // Get lowerbound : this can be improved to support loops where initialization
        // doesn't happen just before the loop.
        Unit initStmt = units.getPredOf(l.getHead());
        if (initStmt == null) {
            System.out.println("Unsupported initializations");
            System.out.println("NOT Parallelizable");
            return;
        }
        if (!(initStmt instanceof JAssignStmt)) {
            System.out.println("InitStmt isn't Assignment");
            System.out.println("NOT Parallelizable");
            return;
        }
        if (initStmt.getDefBoxes().get(0).getValue() != var) {
            System.out.println("var isn't initialized properly");
            System.out.println("NOT Parallelizable");
            return;
        }

        lb = initStmt.getUseBoxes().get(0).getValue();

        // lb should be IntConstant

        if (!(lb instanceof IntConstant)) {
            System.out.println("lb (" + lb + ") not IntConstant!");
            System.out.println("NOT Parallelizable");
            return;
        }

        // Dependency Analysis for Scalars
        System.out.println("Running Dependence analysis for Scalars...");
        HashSet<Local> localVars = new HashSet<>(body.getLocals());
        removeNonLocalVars(localVars, l, updateStatement);

        for (Stmt stmt: l.getLoopStatements()) {
            if (stmt instanceof JAssignStmt) {
                Value leftVal = ((JAssignStmt) stmt).leftBox.getValue();
                if (((stmt != updateStatement) && (leftVal instanceof Local) && (!localVars.contains((Local) leftVal))) ||
                        (leftVal instanceof FieldRef)) {
                    System.out.println("A non-local var or ArrayRef being written to: " + leftVal);
                    System.out.println("NOT Parallelizable");
                    return;
                }
            }
        }

        // Add to loopVars
        loopVars.add((Local) var);

        System.out.println("Scalar analysis complete!");

        // Store all arrayRefs in a Map
        HashMap<Local, ArrayList<ArrayReference>> arrayWrites = new HashMap<>();
        HashMap<Local, ArrayList<ArrayReference>> arrayReads = new HashMap<>();

        for (Stmt stmt: l.getLoopStatements()) {
            if (stmt.containsArrayRef()) {
                Value arrayVar = stmt.getArrayRef().getBase();
                Value indexVar = stmt.getArrayRef().getIndex();
                HashMap<Local, ArrayList<ArrayReference>> tempArray;
                if (stmt instanceof JAssignStmt && (((JAssignStmt) stmt).leftBox.getValue() instanceof ArrayRef)) {
                    tempArray = arrayWrites;
                } else {
                    tempArray = arrayReads;
                }

                if (!addArrayRef(tempArray, stmt, arrayVar, indexVar)) {
                    System.out.println("Improper ArrayRefs!");
                    System.out.println("NOT Parallelizable");
                    return;
                }
            }
        }

        // Functions Calls exist and Not Supported
        if (!checkFunctionCallCompatibility(l)) {
            System.out.println("Has incompatible function calls!");
            System.out.println("NOT Parallelizable");
            return;
        }

        // Check array dependence
        if (!arrayDependence(arrayWrites, arrayReads, (IntConstant) lb, ub, (Local) var, localVars)) {
            System.out.println("Found and Array Dependence!");
            System.out.println("NOT Parallelizable");
            return;
        }

        System.out.println("No Dependence Found! The Loop is Parallelizable!");

        // The loop is parallelizable. Write the annotation to file.
        int index = 0;
        for (Local local : body.getLocals()) {
            if (var.equivTo(local)) {
                break;
            } else if (!local.toString().startsWith("$")) {
                index++;
            }
        }
        int start = getBCI.get(initStmt) + ((index <= 3)?1:2);
        int length = getBCI.get(l.getBackJumpStmt()) + 3 - start;
        annotationMap.addAnnotation(signature, start,
                length, index);

        System.out.println("initBCI: " + start);
        System.out.println("BCIlength: " + length);
        System.out.println("Index: " + index);


    }

    protected static void processCFG(SootMethod method) {
        // Ignore JDK methods for now
        if (method.isJavaLibraryMethod() || method.isStaticInitializer() || method.isPhantom()) {
            return;
        }
        loopVars = new HashSet<>();
        String signature = method.getBytecodeSignature();
        System.out.println("---" + signature + "---");

        Body body = method.getActiveBody();

//        c.transform(body);
        LoopFinder loopFinder = new LoopFinder();
        Set<Loop> loops = loopFinder.getLoops(body);

        graph = new CompleteUnitGraph(body);
        liveVariableAnalysis = new LiveVariableAnalysis(graph);
        ld = new SimpleLocalDefs(graph);

        for(Loop l : loops) {
            System.out.println("Analysing Loop with head: " + correctHead(l.getHead(), l.getLoopStatements()));
            analyseLoop(l, body, signature);
            System.out.println("******************");
        }
        System.out.println("-----------------------");
    }

}
