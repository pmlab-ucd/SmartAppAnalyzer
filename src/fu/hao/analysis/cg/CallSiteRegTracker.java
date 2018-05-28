package fu.hao.analysis.cg;

import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Description: Track the propagation of a call site value in a class
 *
 * @author Hao Fu(haofu@ucdavis.edu)
 * @since 11/9/2016
 */
public class CallSiteRegTracker extends ForwardFlowAnalysis<Object, Object> {
    private Stmt srcStmt;

    @SuppressWarnings("unchecked")
    public CallSiteRegTracker(DirectedGraph<?> exceptionalUnitGraph, Stmt srcStmt) {
        // Use superclass's constructor.
        super((DirectedGraph<Object>) exceptionalUnitGraph);
        this.srcStmt = srcStmt;
        doAnalysis();
    }

    @Override
    protected Object newInitialFlow() {
        return new ArraySparseSet();
    }

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

    @Override
    protected void flowThrough(Object in, Object node, Object out) {
        Unit unit = (Unit) node;
        FlowSet<Value> inSet = (FlowSet<Value>) in,
                outSet = (FlowSet<Value>) out;
        checkBefore(unit, inSet, outSet);
        if (!gen(inSet, unit, outSet)) {
            kill(unit, outSet);
        }

        checkAfter(unit, outSet);
    }

    /*
     * rm tainted var who has been assigned value from un-tainted
     */
    private void kill(Object node, FlowSet<Value> outSet) {
        Unit unit = (Unit)node;
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

    protected void checkBefore(Unit unit, FlowSet inSet, FlowSet outSet) {

    }


    protected void checkAfter(Unit unit, FlowSet<Value> outSet) {
        Stmt stmt = (Stmt)unit;
        if (stmt.containsInvokeExpr()) {
            SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
            if (sootMethod.getName().equals("call")) {
                List<ValueBox> boxes = stmt.getInvokeExprBox().getValue().getUseBoxes();
                if (boxes.size() > 0 && outSet.contains(boxes.get(0).getValue())) {
                    System.out.println("calling ");
                }

            }
        }
    }

    /*
     * add vars possibly tainted
     */
    protected boolean gen(Object in, Object node, Object out) {
        FlowSet inSet = (FlowSet)in,
                outSet = (FlowSet)out;
        Unit unit = (Unit)node;
        copy(inSet, outSet);
        boolean hasTainted = false;
        Stmt stmt = (Stmt)unit;

        if (unit instanceof AssignStmt) {
            // if returned by source()
            if (unit.equals(srcStmt)) {
                System.out.print("Found Source! " + unit);
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

    protected void addDefBox(Unit unit, FlowSet outSet) {
        for (ValueBox defBox : unit.getDefBoxes()) {
            Value value = defBox.getValue();
            if (value instanceof Local) {
                outSet.add(value);
            }
        }
    }

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

}
