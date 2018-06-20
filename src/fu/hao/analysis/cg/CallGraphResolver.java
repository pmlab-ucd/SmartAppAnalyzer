package fu.hao.analysis.cg;

import fu.hao.analysis.AbstractResolver;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolve the call graph of the given groovy class.
 */
public class CallGraphResolver extends AbstractResolver {
    private static final Logger logger = LoggerFactory.getLogger(CallGraphResolver.class);
    /**
     * Add the call edges by "replacing" groovy.runtime.callsite.CallSite::call/callCurrent to the referred real call.
     * If it invokes a call such as $r5.<org.codehaus.groovy.runtime.callsite.CallSite: java.lang.Object call(java.lang.Object,java.lang.Object)>($r6, $r7),
     * instrument a virtual direct call use the call site name, such as multiply(r6, r7) and add the edge to the cg.
     *
     * @param method The analysis target.
     * @param callSites The call sites array.
     */
    @Override
    protected Map<Stmt, Stmt> deriveNewStmts(DirectedGraph<?> cfg, Stmt stmt, String name, SootMethod method) {
        CallSiteRegTracker callSiteRegTracker = new CallSiteRegTracker(cfg, stmt, name, method);
        return callSiteRegTracker.getOld2NewStmts();
    }

    /*
    public static void interpretation(List<Stmt> stmtList) {
        for (Stmt stmt : stmtList) {
            if (stmt.containsInvokeExpr()) {
                SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
                if (sootMethod.getName().equals("call") || sootMethod.getName().equals("callCurrent")) {
                    Value invokeExprValue = stmt.getInvokeExprBox().getValue();
                    if (!stmt.getInvokeExpr().getMethod().isStatic()) {
                        if (invokeExprValue.getUseBoxes().isEmpty()) {
                            Log.err(TAG, "Cannot locate the invoking reg!");
                        }

                    }
                    Value invokingReg = invokeExprValue.getUseBoxes().get(invokeExprValue.getUseBoxes().size() - 1).getValue();

                    Log.bb(TAG, invokingReg + ", " + invokeExprValue);
                    if (reg2names.containsKey(invokingReg.toString())) {
                        //tgtClass.getMethod()
                        Log.msg(TAG, reg2names.get(invokingReg.toString()) + ": " + stmt.getInvokeExpr().getArgs());
                        for (Value arg : stmt.getInvokeExpr().getArgs()) {
                            Log.bb(TAG, arg.getType());
                        }
                    }
                }
            }
        }
    } */
}
