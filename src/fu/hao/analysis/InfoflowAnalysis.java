package fu.hao.analysis;

import fu.hao.analysis.cg.CallGraphResolver;
import fu.hao.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.IfStmt;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.aliasing.IAliasingStrategy;
import soot.jimple.infoflow.cfg.BiDirICFGFactory;
import soot.jimple.infoflow.cfg.DefaultBiDiICFGFactory;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.data.FlowDroidMemoryManager;
import soot.jimple.infoflow.data.pathBuilders.DefaultPathBuilderFactory;
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator;
import soot.jimple.infoflow.memory.FlowDroidMemoryWatcher;
import soot.jimple.infoflow.problems.InfoflowProblem;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;
import soot.jimple.infoflow.solver.memory.DefaultMemoryManagerFactory;
import soot.jimple.infoflow.solver.memory.IMemoryManager;
import soot.jimple.infoflow.solver.memory.IMemoryManagerFactory;
import soot.jimple.infoflow.sourcesSinks.manager.DefaultSourceSinkManager;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.options.Options;

import java.io.File;
import java.util.*;

public class InfoflowAnalysis extends Infoflow {

    private static final Logger logger = LoggerFactory.getLogger(InfoflowAnalysis.class);

    public InfoflowAnalysis(String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory) {
        super(androidPath, forceAndroidJar, icfgFactory);
    }

    public void computeInfoflow(String appPath, String libPath,
                                Collection<String> entryPoints,
                                Collection<String> sources,
                                Collection<String> sinks) {

        DefaultEntryPointCreator entryPointCreator = new DefaultEntryPointCreator(entryPoints);
        initializeSoot(appPath, libPath, entryPointCreator.getRequiredClasses());
        Set<SootClass> targetClasses = new HashSet<>();

//        for (SootClass sootClass : Scene.v().getClasses()) {
//            for (SootMethod sootMethod : sootClass.getMethods()) {
//                if (sootMethod.getSignature().contains("You")) {
//                    logger.info(sootMethod.getSignature());
//                }
//            }
//        }

        // Entry point such as "<YouLeftTheDoorOpen: java.lang.Object sensorTriggered(java.lang.Object)>"
        for (String signature : entryPoints) {
            SootMethod ep = Scene.v().getMethod(signature);
            if (ep.isConcrete()) {
                ep.retrieveActiveBody();
                Scene.v().setEntryPoints(Collections.singletonList(ep));
                Options.v().set_main_class(ep.getDeclaringClass().getName());
                targetClasses.add(ep.getDeclaringClass());
            } else {
                logger.debug("Skipping non-concrete method " + ep);
            }
        }

//        SootField newField = new SootField("field", BooleanType.v(), Modifier.STATIC);
//        Scene.v().getMainClass().addField(newField);
//        AssignStmt toAdd1 = Jimple.v().newAssignStmt(null,
//                Jimple.v().newStaticFieldRef(newField.makeRef()));
        for (SootClass targetClass : targetClasses) {
            Map<Integer, String> callSites = CallGraphResolver.getCallSites(targetClass);
            System.out.println(callSites);

            for (SootMethod method : targetClass.getMethods()) {
                if (method.getName().contains("CallSiteArray") || method.getName().matches("run")) {
                    continue;
                }
                CallGraphResolver.newCallEdges(method, callSites);
            }

            for (Body body : CallGraphResolver.newCallSites.keySet()) {
                Map<Stmt, Stmt> old2New = CallGraphResolver.newCallSites.get(body);
                for (Stmt old : old2New.keySet()) {
                    Stmt newInvoke = old2New.get(old);
                    body.getUnits().insertAfter(newInvoke, old);
//                        Edge edge = new Edge(body.getMethod(), newInvoke, newInvoke.getInvokeExpr().getMethod());
//                        callGraph.addEdge(edge);
//                        body.validate();
                    logger.info(body.getMethod() + ": " + newInvoke);
                }
            }
        }

        for (String signature : entryPoints) {
            SootMethod ep = Scene.v().getMethod(signature);
            if (ep.isConcrete()) {
                for (Unit unit : ep.getActiveBody().getUnits()) {
                    logger.info("" + unit);
                }
            } else {
                logger.debug("Skipping non-concrete method " + ep);
            }
        }

        // super.computeInfoflow(appPath, libPath, entryPoints, sources, sinks);
//        logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());
//
//        DefaultBiDiICFGFactory icfgFactory = new DefaultBiDiICFGFactory();
//        IInfoflowCFG iCfg = icfgFactory.buildBiDirICFG(InfoflowConfiguration.CallgraphAlgorithm.VTA,
//                false);
//
//        // If we don't have a FastHierarchy, we need to create it
//        hierarchy = Scene.v().getOrMakeFastHierarchy();
//
//        DefaultSourceSinkManager ssm = new DefaultSourceSinkManager(sources, sinks);
////        ssm.setParameterTaintMethods(Collections.singletonList(epoint));
////        ssm.setReturnTaintMethods(Collections.singletonList(epoint));
//
//        // runAnalysis(ssm, iCfg);
//        runAnalysis(ssm);
//        logger.info(getResults().toString());
//
//        SootMethod sootMethod = tgtClass.getMethod(args[1], new ArrayList<>());
//        ((JimpleBasedInterproceduralCFG) ((InfoflowCFG) iCfg).getDelegate()).initializeUnitToOwner(sootMethod);
//
//        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
//            Stmt stmt = (Stmt) unit;
//            logger.info("" + stmt);
//        }
//        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
//            Stmt stmt = (Stmt) unit;
//            // Get the conditional.
//            if (stmt instanceof IfStmt) {
//                logger.info("IF: " + stmt);
//                logger.info("post: " + iCfg.getPostdominatorOf(stmt).getUnit());
//            }
//        }
    }

}
