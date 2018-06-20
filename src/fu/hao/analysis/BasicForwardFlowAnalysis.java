package fu.hao.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.*;

/**
 * Description: Backwardly track the source of parameter of setProperty.
 *
 * @author Hao Fu(haofu@ucdavis.edu)
 * @since 6/14/2018
 */
public class BasicForwardFlowAnalysis extends ForwardFlowAnalysis<Object, Object> {
    private static final Logger logger = LoggerFactory.getLogger(BasicForwardFlowAnalysis.class);

    protected Stmt srcStmt;
    protected SootMethod thisMethod;
    protected String name;

    @SuppressWarnings("unchecked")
    public BasicForwardFlowAnalysis(DirectedGraph<?> exceptionalUnitGraph, Stmt srcStmt, String name, SootMethod thisMethod) {
        // Use superclass's constructor.
        super((DirectedGraph<Object>) exceptionalUnitGraph);
        this.srcStmt = srcStmt;
        this.thisMethod = thisMethod;
        this.name = name;
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
    protected void kill(Object node, FlowSet<Value> outSet) {
        Unit unit = (Unit) node;
        if (unit instanceof AssignStmt) {
            for (ValueBox defBox : unit.getDefBoxes()) {
                Value value = defBox.getValue();
                if (value instanceof Local && outSet.contains(value)) {
                    logger.debug("Kill here! " + unit);
                    outSet.remove(value);
                }
            }
        }
    }

    protected void checkBefore(Unit unit, FlowSet inSet, FlowSet outSet) {

    }

    /**
     * If the call site reg invokes a call, add a new call graph edge.
     * @param unit   current statement
     * @param outSet output set
     */
    protected void checkAfter(Unit unit, FlowSet<Value> outSet) {
    }

    @SuppressWarnings("unchecked")
    /*
     * add vars possibly tainted
     */
    protected boolean gen(Object in, Object node, Object out) {
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
    protected void addDefBox(Unit unit, FlowSet outSet) {
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

}
