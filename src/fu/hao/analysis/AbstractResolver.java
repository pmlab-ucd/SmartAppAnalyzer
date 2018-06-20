package fu.hao.analysis;

import heros.solver.Pair;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractResolver {
    protected static Map<SootClass, Map<Integer, String>> resolvedCallSites = new HashMap<>();
    protected static Map<SootMethod, Value> resolvedCallSiteRegs = new HashMap<>();
    protected Map<Body, Map<Stmt, Stmt>> old2New = new HashMap<>();
    /**
     * Groovy stores every call into a call site array. Use this method to retrieve the call sites.
     * @param tgtClass The analysis target
     * @return call sites array info
     */
    public static Map<Integer, String> getCallSites(SootClass tgtClass) {
        if (resolvedCallSites.containsKey(tgtClass)) {
            return resolvedCallSites.get(tgtClass);
        }
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
        resolvedCallSites.put(tgtClass, callSites);
        return callSites;
    }

    /**
     * Get the register that stores the info of call sites in this method.
     * CallSite[] var2 = $getCallSiteArray();
     * @param method The analysis target.
     * @return The reg points to call sites array.
     */
    protected static Value getCallSiteReg(SootMethod method) {
        if (resolvedCallSiteRegs.containsKey(method)) {
            return resolvedCallSiteRegs.get(method);
        }
        // 获得它的函数体
        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        for (Unit unit : cfg) {
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()) {
                SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
                if (sootMethod.getName().contains("getCallSiteArray") && !stmt.getDefBoxes().isEmpty()) {
                    Value value = stmt.getDefBoxes().get(0).getValue();
                    resolvedCallSiteRegs.put(sootMethod, value);
                    return value;
                }
            }
        }
        resolvedCallSiteRegs.put(method, null);
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
        return res;
    }

    public static List<Stmt> slicing(SootMethod method, Stmt tgtStmt) {
        // 获得它的函数体
        Body body = method.retrieveActiveBody();
        // 生成函数的control flow graph
        UnitGraph cfg = new ExceptionalUnitGraph(body);
        ForwardSlicing forwardSlicing = new ForwardSlicing(cfg, tgtStmt);
        return forwardSlicing.getSlice();
    }

    /**
     * The call site stores elements with quotes such as 0="runScript", remove the quotes: 0=runScript.
     * @param name A string such as "xx"
     * @return xx
     */
    protected static String pureName(String name) {
        Pattern p = Pattern.compile("\"([^\"]*)\"");
        Matcher m = p.matcher(name);
        if (m.find()) {
            return (m.group(1));
        }
        return "";
    }

    public Map<Body, Map<Stmt, Stmt>> getOld2NewStmts() {
        return old2New;
    }

    public void deriveNewStmts(SootMethod method, Map<Integer, String> callSites) {
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
                    Map<Stmt, Stmt> subRes = deriveNewStmts(cfg, stmt, name, method);
                    if (subRes != null)
                    old2New.putAll(subRes);
                }
            }
        }
        /**
         * Store <body, <old groovy runtime callsite, new equivalent Java call site>>
         */
        Map<Body, Map<Stmt, Stmt>> newCallSites = getOld2NewStmts();
        newCallSites.put(body, old2New);
    }

    protected abstract Map<Stmt, Stmt> deriveNewStmts(DirectedGraph<?> exceptionalUnitGraph, Stmt stmt, String name, SootMethod method);
}
