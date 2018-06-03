package fu.hao.analysis.cg;

import fu.hao.analysis.DataForwardTracer;
import heros.solver.Pair;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolve the call graph of the given groovy class.
 */
public class CallGraphResolver {
    private static final Logger logger = LoggerFactory.getLogger(CallGraphResolver.class);
    /**
     * Store <body, <old groovy runtime callsite, new equivalent Java call site>>
     */
    public static final Map<Body, Map<Stmt, Stmt>> newCallSites = new HashMap<>();

    /**
     * Groovy stores every call into a call site array. Use this method to retrieve the call sites.
     * @param tgtClass The analysis target
     * @return call sites array info
     */
    public static Map<Integer, String> getCallSites(SootClass tgtClass) {
        Map<Integer, String> callSites = new HashMap<>();
        for (SootMethod method : tgtClass.getMethods()) {
            if (!method.getName().contains("$createCallSiteArray")) {
                continue;
            }
            // 获得它的函数体
            Body body = method.retrieveActiveBody();
            // 生成函数的control flow graph
            UnitGraph cfg = new ExceptionalUnitGraph(body);
            for (Unit unit : cfg) {
                Stmt stmt = (Stmt) unit;
                Value lastVal = null;
                for (int i = 0; i < stmt.getUseBoxes().size(); i++) {
                    Value value = stmt.getUseBoxes().get(i).getValue();
                    if (value.getType().toString().equals("java.lang.String")
                            && lastVal != null && lastVal.getType().toString().contains("nt")) {
                        callSites.put(Integer.parseInt(lastVal.toString()), pureName(value.toString()));
                    }
                    lastVal = value;
                }
            }
        }

        return callSites;
    }

    /**
     * Get the register that stores the info of call sites in this method.
     * CallSite[] var2 = $getCallSiteArray();
     * @param method The analysis target.
     * @return The reg points to call sites array.
     */
    private static Value getCallSiteReg(SootMethod method) {
        // 获得它的函数体
        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        for (Unit unit : cfg) {
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()) {
                SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
                if (sootMethod.getName().contains("getCallSiteArray") && !stmt.getDefBoxes().isEmpty()) {
                    //System.out.println(stmt.getDefBoxes().get(0).getValue());
                    return stmt.getDefBoxes().get(0).getValue();
                }
            }
        }

        return null;
    }

    public static Pair<Map<String, String>, Map<String, Stmt>> getCallees(SootMethod method, Map<Integer, String> callSites) {
        Map<String, String> reg2names = new HashMap<>();
        Map<String, Stmt> name2stmts = new HashMap<>();
        Pair<Map<String, String>, Map<String, Stmt>> res = new Pair<>(reg2names, name2stmts);
        Value nameReg = getCallSiteReg(method);
        if (nameReg == null) {
            return res;
        }
        // TODO It seems we do not need to track the data flow value of the name reg, its value would not be reused.
        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);

        for (Unit unit : cfg) {
            Stmt stmt = (Stmt) unit;
            if (stmt instanceof DefinitionStmt) {
                DefinitionStmt definitionStmt = (DefinitionStmt) stmt;
                if (definitionStmt.getRightOp().toString().contains(nameReg.toString() + "[")) {
                    String index = definitionStmt.getRightOp().toString();
                    index = index.substring(index.indexOf("[") + 1, index.indexOf("]"));
                    String name = callSites.get(Integer.parseInt(index));
                    reg2names.put(definitionStmt.getLeftOp().toString(), name);
                    name2stmts.put(name, stmt);
                }
            }
        }
        logger.debug("reg2names: " + reg2names);
        return res;
    }

    public static List<Stmt> slicing(SootMethod method, Stmt tgtStmt) {
        // 获得它的函数体
        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        DataForwardTracer dataForwardTracer = new DataForwardTracer(cfg, tgtStmt);
        return dataForwardTracer.getSlice();
    }

    /**
     * The call site stores elements with quotes such as 0="runScript", remove the quotes: 0=runScript.
     * @param name A string such as "xx"
     * @return xx
     */
    private static String pureName(String name) {
        Pattern p = Pattern.compile("\"([^\"]*)\"");
        Matcher m = p.matcher(name);
        if (m.find()) {
            return (m.group(1));
        }
        return "";
    }

    /**
     * Add the call edges by "replacing" groovy.runtime.callsite.CallSite::call/callCurrent to the referred real call.
     * If it invokes a call such as $r5.<org.codehaus.groovy.runtime.callsite.CallSite: java.lang.Object call(java.lang.Object,java.lang.Object)>($r6, $r7),
     * instrument a virtual direct call use the call site name, such as multiply(r6, r7) and add the edge to the cg.
     *
     * @param method The analysis target.
     * @param callSites The call sites array.
     */
    public static void addCallEdges(SootMethod method, Map<Integer, String> callSites) {
        Value callSiteReg = getCallSiteReg(method);
        if (callSiteReg == null) {
            return;
        }

        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        Map<Stmt, Stmt> old2New = new HashMap<>();
        for (Unit unit : cfg) {
            Stmt stmt = (Stmt) unit;
            if (stmt instanceof DefinitionStmt) {
                DefinitionStmt definitionStmt = (DefinitionStmt) stmt;
                if (definitionStmt.getRightOp().toString().contains(callSiteReg.toString() + "[")) {
                    String index = definitionStmt.getRightOp().toString();
                    index = index.substring(index.indexOf("[") + 1, index.indexOf("]"));
                    String name = callSites.get(Integer.parseInt(index));
                    logger.debug(index + ", " + name);
                    CallSiteRegTracker callSiteRegTracker = new CallSiteRegTracker(cfg, stmt, name, method);
                    old2New.putAll(callSiteRegTracker.getOld2NewCalls());
                }
            }
        }
        newCallSites.put(body, old2New);
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
