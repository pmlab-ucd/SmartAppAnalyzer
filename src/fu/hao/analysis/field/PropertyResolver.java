package fu.hao.analysis.field;

import fu.hao.analysis.AbstractResolver;
import fu.hao.analysis.cg.CallSiteRegTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.HashMap;
import java.util.Map;

public class PropertyResolver extends AbstractResolver {

    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);

    /**
     * For each get Groovy property such as
     * $r31 = interfaceinvoke $r29.<org.codehaus.groovy.runtime.callsite.CallSite: java.lang.Object callGroovyObjectGetProperty(java.lang.Object)>(this)
     * Replace it with $r31 = <MyClass: myField>
     *
     * @param method The analysis target.
     * @param callSites The call sites array.
     */
    @Override
    protected Map<Stmt, Stmt> deriveNewStmts(DirectedGraph<?> cfg, Stmt stmt, String name, SootMethod method) {
        PropertyTracker propertyTracker = new PropertyTracker(cfg, stmt, name, method);
        return propertyTracker.getOld2NewStmts();
    }
}
