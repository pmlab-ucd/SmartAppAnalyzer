package fu.hao.analysis.cg;

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
public class CallSiteRegTracker extends ForwardFlowAnalysis<Object, Object> {
    private static final Logger logger = LoggerFactory.getLogger(CallSiteRegTracker.class);

    private Stmt srcStmt;
    private SootMethod thisMethod;
    private String name;
    /**
     * The map stores the relationships between the old groovy call and the new referred invocation.
     */
    private Map<Stmt, Stmt> old2NewCalls = new HashMap<>();

    @SuppressWarnings("unchecked")
    public CallSiteRegTracker(DirectedGraph<?> exceptionalUnitGraph, Stmt srcStmt, String name, SootMethod thisMethod) {
        // Use superclass's constructor.
        super((DirectedGraph<Object>) exceptionalUnitGraph);
        this.srcStmt = srcStmt;
        this.thisMethod = thisMethod;
        this.name = name;
        doAnalysis();
    }

    @Override
    protected Object newInitialFlow() {
        return new ArraySparseSet();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void copy(Object src, Object dest) {
        FlowSet srcSet = (FlowSet) src,
                destSet = (FlowSet) dest;
        srcSet.copy(destSet);
    }

    @Override
    protected Object entryInitialFlow() {
        return new ArraySparseSet();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void flowThrough(Object in, Object node, Object out) {
        Unit unit = (Unit) node;
        if (in instanceof FlowSet && out instanceof FlowSet) {
            FlowSet<Value> inSet = (FlowSet<Value>) in;
            FlowSet<Value> outSet = (FlowSet<Value>) out;
            checkBefore(unit, inSet, outSet);
            if (!gen(inSet, unit, outSet)) {
                kill(unit, outSet);
            }

            checkAfter(unit, outSet);
        }
    }

    /*
     * rm tainted var who has been assigned value from un-tainted
     */
    private void kill(Object node, FlowSet<Value> outSet) {
        Unit unit = (Unit) node;
        if (unit instanceof AssignStmt) {
            for (ValueBox defBox : unit.getDefBoxes()) {
                Value value = defBox.getValue();
                if (value instanceof Local && outSet.contains(value)) {
                    System.out.println("Kill here! " + unit);
                    outSet.remove(value);
                }
            }
        }
    }

    private void checkBefore(Unit unit, FlowSet inSet, FlowSet outSet) {

    }

    /**
     * If the call site reg invokes a call, add a new call graph edge.
     * @param unit   current statement
     * @param outSet output set
     */
    private void checkAfter(Unit unit, FlowSet<Value> outSet) {
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
                    old2NewCalls.put(stmt, newInvoke);
                    logger.debug("calling " + callee + ", " + newInvoke);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    /*
     * add vars possibly tainted
     */
    private boolean gen(Object in, Object node, Object out) {
        FlowSet inSet = (FlowSet) in,
                outSet = (FlowSet) out;
        Unit unit = (Unit) node;
        copy(inSet, outSet);
        boolean hasTainted = false;
        Stmt stmt = (Stmt) unit;

        if (unit instanceof AssignStmt) {
            // if returned by source()
            if (unit.equals(srcStmt)) {
                logger.debug("Found Source! " + unit);
                addDefBox(unit, outSet);
                hasTainted = true;
            }
        }

        for (ValueBox valueBox : stmt.getUseBoxes()) {
            Value useVal = valueBox.getValue();
            if (inSet.contains(useVal)) {
                addDefBox(unit, outSet);
                hasTainted = true;
                break;
            }
        }

        return hasTainted;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void merge(Object in1, Object in2, Object out) {
        FlowSet inSet1 = (FlowSet) in1,
                inSet2 = (FlowSet) in2,
                outSet = (FlowSet) out;
        inSet1.union(inSet2, outSet);
    }

    @SuppressWarnings("unchecked")
    public List<Local> getLiveLocalsAfter(Unit s) {
        // ArraySparseSet returns a unbacked list of elements!
        return ((ArraySparseSet) getFlowAfter(s)).toList();
    }

    @SuppressWarnings("unchecked")
    public List<Local> getLiveLocalsBefore(Unit s) {
        // ArraySparseSet returns a unbacked list of elements!
        return ((ArraySparseSet) getFlowBefore(s)).toList();
    }

    @SuppressWarnings("unchecked")
    private void addDefBox(Unit unit, FlowSet outSet) {
        for (ValueBox defBox : unit.getDefBoxes()) {
            Value value = defBox.getValue();
            if (value instanceof Local) {
                outSet.add(value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected boolean leftTaintedAsRightTainted(Unit unit, FlowSet inSet, FlowSet outSet) {
        for (ValueBox useBox : unit.getUseBoxes()) {
            Value useVal = useBox.getValue();
            if (inSet.contains(useVal)) {
                addDefBox(unit, outSet);
                return true;
            }
        }

        return false;
    }

    public Map<Stmt, Stmt> getOld2NewCalls() {
        return old2NewCalls;
    }
}
