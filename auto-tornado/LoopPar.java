import java.io.*;
import java.lang.annotation.Annotation;
import java.util.*;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.internal.*;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.jimple.toolkits.annotation.logic.LoopFinder;
import soot.jimple.toolkits.scalar.ConstantPropagatorAndFolder;
import uk.ac.manchester.tornado.annotation.AnnotationMap;

public class LoopPar {
    public static void main(String[] args) {
        String classPath = ".:" + args[0];

        String[] sootArgs = { "-cp", classPath, "-pp", "-w", "-f", "J", "-keep-line-number", "-keep-bytecode-offset" , "-no-bodies-for-excluded",
                "-p", "jb", "use-original-names",
                "-main-class", args[1], args[1], "-O" };
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.pea", new STPurity()));
        soot.Main.main(sootArgs);
    }

}

class STPurity extends SceneTransformer {
    static ConstantPropagatorAndFolder c = ConstantPropagatorAndFolder.v();
    static AnnotationMap annotationMap = new AnnotationMap();


    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {
        System.out.println("Starting analysis");
        System.out.println(Scene.v().getMainClass());
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
            oos.writeObject(annotationMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void tornadoVMSupported(Loop l, Body body, String signature) {

        PatchingChain<Unit> units = body.getUnits();

        List<Stmt> statements = l.getLoopStatements();
        for (Stmt s : statements) {
            System.out.println(s + ": " + getBCI.get(s));
        }
        Stmt head = l.getHead();
        for (Stmt s : statements) {
            if (s instanceof JIfStmt) {
                head = s;
                break;
            }
        }
        Stmt backJumpStatement = l.getBackJumpStmt();
        Stmt updateStatement = statements.get(statements.size()-2); // must be atleast 2: head, back jump
        Value var, inc, lb, ub;

        // BackJumpStatement at the end?
        if (backJumpStatement != statements.get(statements.size()-1)) {
            System.out.println("BackJumpFail");
            return;
        }


        // updateStatement assignment?
        if (!(updateStatement instanceof JAssignStmt)) {
            System.out.println("Update isn't Assignment");
            return;
        }

        var = updateStatement.getDefBoxes().get(0).getValue();

        // check if this var appears in the head
        boolean found = false;
        for (ValueBox v : head.getUseBoxes()) {
            if (v.getValue() == var) {
                found = true;
                break;
            }
        }

        if (!found) {
            System.out.println("Var isn't in Head");
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
            return;
        }

        if (!(updateStatement.getUseBoxes().get(0).getValue() instanceof JAddExpr)) {
            System.out.println("RHS(Update) isn't Add Expr");
            return;
        }

        if (updateStatement.getUseBoxes().size() != 3) {
            System.out.println("RHS(Update) doesn't have 3 elements");
            return;
        }

        if (updateStatement.getUseBoxes().get(1).getValue() == var) {
            inc = updateStatement.getUseBoxes().get(2).getValue();
        } else if (updateStatement.getUseBoxes().get(2).getValue() == var) {
            inc = updateStatement.getUseBoxes().get(1).getValue();
        } else {
            System.out.println("Update RHS doesn't have var");
            return;
        }

        // var not in defBox
        for (int i=0; i<statements.size()-2; i++) {
            for (ValueBox v : statements.get(i).getDefBoxes()) {
                if (v.getValue() == var) {
                    System.out.println("Var being assigned");
                    return;
                }
            }
        }

        // No breaks : single exit statement
        if (!l.hasSingleExit()) {
            System.out.println("Multiple Exits");
            return;
        }

        // Get lowerbound : this can be improved to support loops where initialization
        // doesn't happen just before the loop.
        Unit initStmt = units.getPredOf(l.getHead());
        System.out.println("InitSTMT: " + initStmt);
        if (initStmt == null) {
            System.out.println("No initializations");
            return;
        }
        if (!(initStmt instanceof JAssignStmt)) {
            System.out.println("Init isn't Assignment");
            return;
        }
        if (initStmt.getDefBoxes().get(0).getValue() != var) {
            System.out.println("var isn't initialized");
            return;
        }

        lb = initStmt.getUseBoxes().get(0).getValue();

        // types of var, ub, inc
        // may want to check if they are JimpleLocal
        // This can be improved to remove checks on ub
        // int, byte, long etc.
//        if (!(var.getType().toString().equals("int")
//                && (ub.getType().toString().equals("int")
//                    || ub instanceof IntConstant)
//                && (inc.getType().toString().equals("int")
//                    || inc instanceof IntConstant))) {
//            System.out.println("Unsupported type of var, ub or inc");
//            System.out.println(var.getType().toString().equals("int"));
//            System.out.println("var: " + var);
//            System.out.println("ub: " + ub);
//            System.out.println(ub.getType().toString());
//            System.out.println(ub instanceof IntConstant);
//            System.out.println("inc: " + inc);
//            System.out.println(inc.getType().toString());
//            System.out.println(inc instanceof IntConstant);
//            return;
//        }


        int index = 0;
        for (Local local : body.getLocals()) {
            if (var.equivTo(local)) {
                break;
            } else if (!local.toString().startsWith("$")) {
                index++;
            }
        }
        int start = getBCI.get(initStmt) + 2;
        int length = getBCI.get(l.getBackJumpStmt()) - getBCI.get(initStmt) + 1;
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

        String signature = method.getBytecodeSignature();
        System.out.println("---" + signature + "---");

        Body body = method.getActiveBody();
//        c.transform(body);
        PatchingChain<Unit> units = body.getUnits();
        LoopFinder loopFinder = new LoopFinder();
        Set<Loop> loops = loopFinder.getLoops(body);
        int i = 0;
        for(Loop l : loops) {
            tornadoVMSupported(l, body, signature);
        }

        for (Unit u : units) {
            System.out.println(u);

        }
        body.validate();

        System.out.println("-----------------------");
    }

}
