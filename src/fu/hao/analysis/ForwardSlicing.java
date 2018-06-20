package fu.hao.analysis;

import soot.Local;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Description: Track the propagation of a call site value in a class
 *
 * @author Hao Fu(haofu@ucdavis.edu)
 * @since 11/9/2016
 */
public class ForwardSlicing extends BasicForwardFlowAnalysis {
    private List<Stmt> slice = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public ForwardSlicing(DirectedGraph<?> exceptionalUnitGraph, Stmt srcStmt) {
        // use superclass's constructor
        super((DirectedGraph<Object>) exceptionalUnitGraph, srcStmt, "", null);
        doAnalysis();
    }

    /*
     * add vars possibly tainted
     */
    @Override
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
                slice.add(stmt);
            }
        }

        for (ValueBox valueBox : stmt.getUseBoxes()) {
            Value useVal = valueBox.getValue();
            if (inSet.contains(useVal)) {
                addDefBox(unit, outSet);
                hasTainted = true;
                slice.add(stmt);
                break;
            }
        }

        return hasTainted;
    }

    public List<Stmt> getSlice() {
        return slice;
    }


}