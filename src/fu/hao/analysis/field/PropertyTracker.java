package fu.hao.analysis.field;

import fu.hao.analysis.BasicForwardFlowAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.FlowSet;

import java.util.*;

/**
 * Description: Backwardly track the source of parameter of setProperty.
 *
 * @author Hao Fu(haofu@ucdavis.edu)
 * @since 6/14/2018
 */
public class PropertyTracker extends BasicForwardFlowAnalysis {
    private static final Logger logger = LoggerFactory.getLogger(PropertyTracker.class);

    /**
     * The map stores the relationships between the old groovy call and the new referred invocation.
     */
    private Map<Stmt, Stmt> old2NewFieldOps = new HashMap<>();
    private static Set<String> addedFields = new HashSet<>();

    @SuppressWarnings("unchecked")
    public PropertyTracker(DirectedGraph<?> exceptionalUnitGraph, Stmt srcStmt, String name, SootMethod thisMethod) {
        // Use superclass's constructor.
        super((DirectedGraph<Object>) exceptionalUnitGraph, srcStmt, name, thisMethod);
        doAnalysis();
    }

    /**
     * If the call site reg invokes a call, add a new call graph edge.
     * @param unit   current statement
     * @param outSet output set
     */
    protected void checkAfter(Unit unit, FlowSet<Value> outSet) {
        Stmt stmt = (Stmt) unit;
        if (stmt.containsInvokeExpr()) {
            SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
            if (sootMethod.getName().matches("callGroovyObjectGetProperty")) {
                List<ValueBox> boxes = stmt.getInvokeExpr().getUseBoxes();
                Value caller;
                if (boxes.size() > 0) {
                    caller = boxes.get(boxes.size() - 1).getValue();
                } else {
                    throw new RuntimeException("Not a valid Groovy get property: " + stmt);
                }
                if (outSet.contains(caller)) {
                    if (!(stmt instanceof AssignStmt)) {
                        throw new RuntimeException("Not a valid Groovy get property: " + stmt);
                    }

                    logger.debug(caller + ", " + stmt.getInvokeExpr() + ", " + stmt.getInvokeExprBox().getValue().getUseBoxes());
                    SootField field;
                    if (addedFields.contains(name)) {
                        field = Scene.v().getMainClass().getFieldByName(name);
                    } else {
                        // Add new field
                        field = new SootField(name, stmt.getInvokeExpr().getMethod().getReturnType(),
                                Modifier.STATIC);
                        Scene.v().getMainClass().addField(field);
                        addedFields.add(name);
                    }

                    AssignStmt toAdd = Jimple.v().newAssignStmt(((AssignStmt) stmt).getLeftOp(),
                            Jimple.v().newStaticFieldRef(field.makeRef()));
                    old2NewFieldOps.put(stmt, toAdd);
                    logger.info("adding " + stmt + ", " + toAdd);
                }
            }
        }
    }

    public Map<Stmt, Stmt> getOld2NewStmts() {
        return old2NewFieldOps;
    }
}
