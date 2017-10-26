package fu.hao.analysis;

import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Description:
 *
 * @author Hao Fu(haofu@ucdavis.edu)
 * @since 2017/10/25
 */
public class MethodSummary {
    private SootMethod method = null;
    private Map<String, String> reg2names = new HashMap<>();
    private Map<String, Stmt> name2stmts = new HashMap<>();

    public static Value getNameReg(SootMethod method) {
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

    public MethodSummary(SootMethod method, Map<Integer, String> callSites) {
        this.method = method;
        Value nameReg = getNameReg(method);
        if (nameReg == null) {
            return;
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
    }

    public void interpretation(List<Stmt> stmtList) {
        for (Stmt stmt : stmtList) {
            if (stmt.containsInvokeExpr()) {
                SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
                if (sootMethod.getName().equals("call") || sootMethod.getName().equals("callCurrent")) {
                    Value invokeExprValue = stmt.getInvokeExprBox().getValue();
                    for (ValueBox valueBox : invokeExprValue.getUseBoxes()) {
                        if (reg2names.containsKey(valueBox.getValue().toString())) {
                            //tgtClass.getMethod()
                            System.out.println(reg2names.get(valueBox.getValue().toString()) + "!");
                        }
                    }
                }
            }
        }
    }

    public Map<String, Stmt> getName2stmts() {
        return name2stmts;
    }
}
