package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryTemplate;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt;

@NodeInfo
public class FixedArrayNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<FixedArrayNode> TYPE = NodeClass
            .create(FixedArrayNode.class);

    @Input
    protected ConstantNode length;

    protected OCLKind elementKind;
    protected OCLMemoryBase memoryRegister;

    public FixedArrayNode(OCLMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elementKind = OCLKind.fromResolvedJavaType(elementType);
    }

    public FixedArrayNode(ResolvedJavaType elementType, ConstantNode length) {
        this(OCLArchitecture.hp, elementType, length);
    }

    public OCLMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        /*
         * using as_T reinterprets the data as type T - consider: float x =
         * (float) 1; and int value = 1, float x = &(value);
         */
        final Value lengthValue = gen.operand(length);
//        System.out.printf("gen operand: %s (%s)\n", lengthValue, lengthValue.getClass().getName());

        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        final OCLBinary.Expr declaration = new OCLBinary.Expr(OCLBinaryTemplate.NEW_ARRAY, lirKind, variable, lengthValue);

        final OCLLIRStmt.ExprStmt expr = new OCLLIRStmt.ExprStmt(declaration);

//        System.out.printf("expr: %s\n", expr);
        gen.getLIRGeneratorTool().append(expr);

        gen.setResult(this, variable);
    }

}
