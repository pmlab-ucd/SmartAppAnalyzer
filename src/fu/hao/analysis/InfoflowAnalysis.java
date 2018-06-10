package fu.hao.analysis;

import fu.hao.analysis.cg.CallGraphResolver;
import fu.hao.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.IfStmt;
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

    public static List<String> entry_points = new ArrayList<>();
    public static List<String> sources = new ArrayList<>();
    public static List<String> sinks = new ArrayList<>();

    public InfoflowAnalysis(String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory) {
        super(androidPath, forceAndroidJar, icfgFactory);
    }

    private static SootClass initializeSoot(String className) {
        // reset Soot:
        soot.G.reset();

        String sep = File.separator;
        String pathSep = File.pathSeparator;
        String path = "lib/rt.jar"; //System.getProperty("java.home") + sep + "lib" + sep + "rt.jar";
        path += pathSep + "." + sep + "out\\test\\SmartAppAnalyzer";
        path += pathSep + "lib/groovy-all-2.2.0-beta-1.jar";
        // path += pathSep + args[0];
        Options.v().set_soot_classpath(path);

        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_output_format(Options.output_format_jimple);

        // Configure the callgraph algorithm
        Options.v().setPhaseOption("cg.spark", "on");
        Options.v().setPhaseOption("cg.spark", "vta:true");
        Options.v().setPhaseOption("cg.spark", "string-constants:true");

        // Specify additional options required for the callgraph
        Options.v().set_whole_program(true);
        Options.v().setPhaseOption("cg", "trim-clinit:false");
        Options.v().setPhaseOption("cg", "types-for-invoke:true");

        // do not merge variables (causes problems with PointsToSets)
        Options.v().setPhaseOption("jb.ulp", "off");

        // load all entryPoint classes with their bodies
        Scene.v().addBasicClass(className, SootClass.BODIES);
        Scene.v().loadNecessaryClasses();
        logger.info("Basic class loading done.");

        SootClass c = Scene.v().forceResolve(className, SootClass.BODIES);
        if (c != null) {
            c.setApplicationClass();
            if (c.isPhantomClass() || c.isPhantom()) {
                logger.info("Only phantom classes loaded, skipping analysis...");
            }
        }

        // do not merge variables (causes problems with PointsToSets)
        Options.v().setPhaseOption("jb.ulp", "off");

        // To cope with broken APK files, we convert all classes that are still
        // dangling after resolution into phantoms
        Scene.v().getClasses().stream().filter(sc -> sc.resolvingLevel() == SootClass.DANGLING).forEach(sc -> {
            sc.setResolvingLevel(SootClass.BODIES);
            sc.setPhantomClass();
        });

        return c;
    }

    private void runAnalysis(ISourceSinkManager sourcesSinks, IInfoflowCFG iCfg) {

        // Make sure that we have a path builder factory
        if (pathBuilderFactory == null)
            pathBuilderFactory = new DefaultPathBuilderFactory(config.getPathConfiguration());

        results = new InfoflowResults();
        memoryWatcher = new FlowDroidMemoryWatcher(results);

        // Create the executor that takes care of the workers
        int numThreads = Runtime.getRuntime().availableProcessors();
        InterruptableExecutor executor = createExecutor(numThreads, true);

        // Initialize the memory manager
        FlowDroidMemoryManager.PathDataErasureMode erasureMode = FlowDroidMemoryManager.PathDataErasureMode.EraseAll;
        erasureMode = FlowDroidMemoryManager.PathDataErasureMode.KeepOnlyContextData;
        erasureMode = FlowDroidMemoryManager.PathDataErasureMode.EraseNothing;
        IMemoryManagerFactory memoryManagerFactory = new DefaultMemoryManagerFactory();
        IMemoryManager<Abstraction, Unit> memoryManager = memoryManagerFactory.getMemoryManager(false, erasureMode);

        // Initialize the data flow manager
        manager = new InfoflowManager(config, null, iCfg, sourcesSinks, taintWrapper, hierarchy,
                new AccessPathFactory(config));

        // Initialize the alias analysis
        IAliasingStrategy aliasingStrategy = createAliasAnalysis(sourcesSinks, iCfg, executor, memoryManager);

        // Get the zero fact
        Abstraction zeroValue = aliasingStrategy.getSolver() != null
                ? aliasingStrategy.getSolver().getTabulationProblem().createZeroValue() : null;

        // Initialize the aliasing infrastructure
        Aliasing aliasing = new Aliasing(aliasingStrategy, manager);
        if (dummyMainMethod != null)
            aliasing.excludeMethodFromMustAlias(dummyMainMethod);

        // Initialize the data flow problem
        InfoflowProblem forwardProblem = new InfoflowProblem(manager, aliasingStrategy, aliasing, zeroValue);

        // We need to create the right data flow solver
        IInfoflowSolver forwardSolver = createForwardSolver(executor, forwardProblem);

        // Set the options
        manager.setForwardSolver(forwardSolver);
        if (aliasingStrategy.getSolver() != null)
            aliasingStrategy.getSolver().getTabulationProblem().getManager().setForwardSolver(forwardSolver);

        // memoryWatcher.addSolver((IMemoryBoundedSolver) forwardSolver);

        forwardSolver.setMemoryManager(memoryManager);
        // forwardSolver.setEnableMergePointChecking(true);

        // forwardProblem.setTaintPropagationHandler(taintPropagationHandler);
        forwardProblem.setTaintWrapper(taintWrapper);
        if (nativeCallHandler != null)
            forwardProblem.setNativeCallHandler(nativeCallHandler);

        if (aliasingStrategy.getSolver() != null) {
            aliasingStrategy.getSolver().getTabulationProblem().setActivationUnitsToCallSites(forwardProblem);
        }

    }

    public void computeInfoflow(String appPath, String libPath,
                    Collection<String> entryPoints,
                    Collection<String> sources,
                    Collection<String> sinks) {
//
//        logger.info("Setting classpath to: " + args[0]);
//        Settings.setAppName(args[0]);
//        Settings.setOutputDirectory("output");
//        Settings.setLogLevel(0);
//
//        SootClass tgtClass = initializeSoot(Settings.getAppName());



        DefaultEntryPointCreator entryPointCreator = new DefaultEntryPointCreator(entryPoints);
        initializeSoot(appPath, libPath, entryPointCreator.getRequiredClasses());
//        Collection<String> classes = entryPointCreator.getRequiredClasses();
        Set<SootClass> targetClasses = new HashSet<>();
//
//        // load all entryPoint classes with their bodies
//        for (String className : classes)
//            Scene.v().addBasicClass(className, SootClass.BODIES);
//        Scene.v().loadNecessaryClasses();
//        logger.info("Basic class loading done.");

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

//        PackManager.v().getPack("wjpp").apply();
//        PackManager.v().getPack("cg").apply();
//        CallGraph callGraph = Scene.v().getCallGraph();
        for (SootClass targetClass : targetClasses) {
            Map<Integer, String> callSites = CallGraphResolver.getCallSites(targetClass);
            System.out.println(callSites);


//            logger.info("cg size: " + String.valueOf(callGraph.size()));
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

        super.computeInfoflow(appPath, libPath, entryPoints, sources, sinks);
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
