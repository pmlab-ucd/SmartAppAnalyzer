package fu.hao.analysis.cg;

import fu.hao.analysis.DataForwardTracer;
import fu.hao.utils.Log;
import heros.solver.Pair;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve the call graph of the given groovy class.
 */
public class CallGraphResolver {
    public static final String TAG = "CallGraphResolver";
    private static Map<SootClass, Map<String, Set<SootMethod>>> class2Methods = new HashMap<>();

    public static Map<String, Set<SootMethod>> getMethods(SootClass sootClass) {
        if (class2Methods.containsKey(sootClass)) {
            return class2Methods.get(sootClass);
        }
        Map<String, Set<SootMethod>> methods = new HashMap<>();
        for (SootMethod method : sootClass.getMethods()) {
            if (!methods.containsKey(method)) {
                methods.put(method.getName(), new HashSet<>());
            }
            methods.get(method.getName()).add(method);
        }
        class2Methods.put(sootClass, methods);
        return methods;
    }

    /**
     * Groovy stores every call into a call site array. Use this method to retrieve the call sites.
     * @param tgtClass
     * @return
     */
    public static Map<Integer, String> getCallSites(SootClass tgtClass) {
        Map<Integer, String> callSites = new HashMap<>();
        if (!CallGraphResolver.class2Methods.containsKey(tgtClass)) {
            getMethods(tgtClass);
        }
        for (String methodName : getMethods(tgtClass).keySet()) {
            if (!methodName.contains("$createCallSiteArray")) {
                continue;
            }
            for (SootMethod method : getMethods(tgtClass).get(methodName)) {
                System.out.println("method: " + method);
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
        }

        return callSites;
    }

    /**
     * Get the register that stores the info of call sites in this method.
     * CallSite[] var2 = $getCallSiteArray();
     * @param method
     * @return
     */
    public static Value getCallSiteReg(SootMethod method) {
        // 获得它的函数体
        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        for (Unit unit : cfg) {
            Stmt stmt = (Stmt)unit;
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
            Stmt stmt = (Stmt)unit;
            if (stmt instanceof DefinitionStmt) {
                DefinitionStmt definitionStmt = (DefinitionStmt) stmt;
                if (definitionStmt.getRightOp().toString().contains(nameReg.toString() + "[")) {
                    String index = definitionStmt.getRightOp().toString();
                    index = index.substring(index.indexOf("[") + 1, index.indexOf("]"));
                    String name = callSites.get(Integer.parseInt(index));
                    //System.out.println(definitionStmt.getLeftOp() + "=" + definitionStmt.getRightOp() + ", " + name);
                    reg2names.put(definitionStmt.getLeftOp().toString(), name);
                    name2stmts.put(name, stmt);
                }
            }
        }
        Log.bb(TAG, reg2names);
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

    private static String pureName(String name) {
        Pattern p = Pattern.compile("\"([^\"]*)\"");
        Matcher m = p.matcher(name);
        while (m.find()) {
            return(m.group(1));
        }
        return "";
    }

    public static void addCallEdges(CallGraph callGraph, SootMethod method, Map<Integer, String> callSites) {
        Value callSiteReg = getCallSiteReg(method);
        if (callSiteReg == null) {
            return;
        }

        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);

        for (Unit unit : cfg) {
            Stmt stmt = (Stmt)unit;
            if (stmt instanceof DefinitionStmt) {
                DefinitionStmt definitionStmt = (DefinitionStmt) stmt;
                if (definitionStmt.getRightOp().toString().contains(callSiteReg.toString() + "[")) {
                    String index = definitionStmt.getRightOp().toString();
                    index = index.substring(index.indexOf("[") + 1, index.indexOf("]"));
                    String name = callSites.get(Integer.parseInt(index));
                    Log.bb(TAG, index + ", " + name);
                    //System.out.println(definitionStmt.getLeftOp() + "=" + definitionStmt.getRightOp() + ", " + name);
                    CallSiteRegTracker callSiteRegTracker = new CallSiteRegTracker(cfg, stmt, name, method);
                }
            }
        }


        for (Stmt old : CallSiteRegTracker.getOld2NewCalls().keySet()) {
            Stmt newInvoke = CallSiteRegTracker.getOld2NewCalls().get(old);
            body.getUnits().insertAfter(newInvoke, old);
            Edge edge = new Edge(method, newInvoke, newInvoke.getInvokeExpr().getMethod());
            callGraph.addEdge(edge);
            for (Unit unit : cfg) {
                Stmt stmt = (Stmt) unit;
                Log.msg(TAG, stmt + ", " + stmt.hashCode());
            }
        }
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
