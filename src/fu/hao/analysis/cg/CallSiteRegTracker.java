package fu.hao.analysis.cg;

import fu.hao.analysis.BasicForwardFlowAnalysis;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Description: Track the propagation of a call site value in a class.
 * If it invokes a call such as $r5.<org.codehaus.groovy.runtime.callsite.CallSite: java.lang.Object call(java.lang.Object,java.lang.Object)>($r6, $r7),
 * return a virtual direct call use the call site name, such as multiply(r6, r7).
 *
 * @author Hao Fu(haofu@ucdavis.edu)
 * @since 11/9/2016
 */
public class CallSiteRegTracker extends BasicForwardFlowAnalysis {
    private static final Logger logger = LoggerFactory.getLogger(CallSiteRegTracker.class);

    /**
     * The map stores the relationships between the old groovy call and the new referred invocation.
     */
    private Map<Stmt, Stmt> old2NewCalls;

    @SuppressWarnings("unchecked")
    public CallSiteRegTracker(DirectedGraph<?> exceptionalUnitGraph, Stmt srcStmt, String name, SootMethod thisMethod) {

        // Use superclass's constructor.
        super((DirectedGraph<Object>) exceptionalUnitGraph, srcStmt, name, thisMethod);
        old2NewCalls = new HashMap<>();
        doAnalysis();
    }

    /**
     * If the call site reg invokes a call, add a new call graph edge.
     *
     * @param unit   current statement
     * @param outSet output set
     */
    @Override
    protected void checkAfter(Unit unit, FlowSet<Value> outSet) {
        Stmt stmt = (Stmt) unit;
        if (stmt.containsInvokeExpr()) {
            SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
            if (sootMethod.getName().matches("call|callCurrent")) {
                List<ValueBox> boxes = stmt.getInvokeExpr().getUseBoxes();
                Value caller;
                if (boxes.size() > 0) {
                    caller = boxes.get(boxes.size() - 1).getValue();
                } else {
                    throw new RuntimeException("Not a valid Groovy call: " + stmt);
                }
                if (outSet.contains(caller)) {
                    logger.debug(caller + ", " + stmt.getInvokeExpr() + ", " + stmt.getInvokeExprBox().getValue().getUseBoxes());
                    SootMethod callee;
                    try {
                        callee = thisMethod.getDeclaringClass().getMethod(name, sootMethod.getParameterTypes());
                    } catch (RuntimeException e) {
                        callee = new SootMethod(name, sootMethod.getParameterTypes(), sootMethod.getReturnType());
                        callee.setDeclaringClass(thisMethod.getDeclaringClass());
                        callee.setDeclared(true);
                    }

                    //Stmt newInvoke = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr((Local) caller, callee.makeRef(), stmt.getInvokeExpr().getArgs()));
                    Stmt newInvoke = (Stmt) stmt.clone();
                    newInvoke.getInvokeExpr().setMethodRef(callee.makeRef());
                    logger.info(old2NewCalls + "," + newInvoke + "," + stmt);
                    this.old2NewCalls.put(stmt, newInvoke);
                    logger.debug("calling " + callee + ", " + newInvoke);
                }
            }
        }
    }

    public Map<Stmt, Stmt> getOld2NewStmts() {
        return old2NewCalls;
    }
}
