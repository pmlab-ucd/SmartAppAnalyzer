/*
 * Warning:
 * This can only be executed correctly when using SOOTCLASSES
 * and put the /bin/ as External Class folder suggested as
 * http://stackoverflow.com/questions/20282481/loading-java-class-files-for-soot-dynamically-in-eclipse.
 * Not working anymore if use soot-trunk.jar as lib
 */

package fu.hao.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import fu.hao.analysis.cg.CallGraphResolver;
import fu.hao.utils.Settings;
import heros.InterproceduralCFG;
import soot.*;
import soot.jimple.IfStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.cfg.DefaultBiDiICFGFactory;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.options.Options;
import soot.SootMethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main entry of analysis.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static SootClass initializeSoot(String className) {
        // reset Soot:
        soot.G.reset();

        String sep = File.separator;
        String pathSep = File.pathSeparator;
        String path = "lib/rt.jar"; //System.getProperty("java.home") + sep + "lib" + sep + "rt.jar";
        path += pathSep + "." + sep + "out\\production\\SmartAppAnalyzer";
        path += pathSep + "lib/groovy-all-2.2.0-beta-1.jar";
        //path += pathSep + args[0];
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

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java Main class_name_to_analyse");
            System.exit(0);
        }

        logger.info("Setting classpath to: " + args[0]);
        Settings.setAppName(args[0]);
        Settings.setOutputDirectory("output");
        Settings.setLogLevel(0);

        SootClass tgtClass = initializeSoot(Settings.getAppName());
        SootMethod ep = Scene.v().getMethod("<YouLeftTheDoorOpen: java.lang.Object sensorTriggered(java.lang.Object)>");
        if (ep.isConcrete())
            ep.retrieveActiveBody();
        else {
            logger.debug("Skipping non-concrete method " + ep);
            return;
        }
        Scene.v().setEntryPoints(Collections.singletonList(ep));
        Options.v().set_main_class(ep.getDeclaringClass().getName());

        PackManager.v().getPack("wjpp").apply();
        PackManager.v().getPack("cg").apply();

        Map<Integer, String> callSites = CallGraphResolver.getCallSites(tgtClass);
        System.out.println(callSites);
        CallGraph callGraph = Scene.v().getCallGraph();

        logger.info("cg size: " + String.valueOf(callGraph.size()));

        for (SootMethod method : tgtClass.getMethods()) {
            if (method.getName().contains("CallSiteArray") || method.getName().matches("run")) {
                continue;
            }
            CallGraphResolver.addCallEdges(method, callSites);
        }

        for (Body body : CallGraphResolver.newCallSites.keySet()) {
            Map<Stmt, Stmt> old2New = CallGraphResolver.newCallSites.get(body);
            for (Stmt old : old2New.keySet()) {
                Stmt newInvoke = old2New.get(old);
                body.getUnits().insertAfter(newInvoke, old);
                Edge edge = new Edge(body.getMethod(), newInvoke, newInvoke.getInvokeExpr().getMethod());
                callGraph.addEdge(edge);
                body.validate();
                logger.info(body.getMethod() + ": " + newInvoke);
            }
        }
        logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());

        DefaultBiDiICFGFactory icfgFactory = new DefaultBiDiICFGFactory();
        IInfoflowCFG iCfg = icfgFactory.buildBiDirICFG(InfoflowConfiguration.CallgraphAlgorithm.VTA,
                false);
        SootMethod sootMethod = tgtClass.getMethod("takeAction", new ArrayList<>());
        ((JimpleBasedInterproceduralCFG) ((InfoflowCFG) iCfg).getDelegate()).initializeUnitToOwner(sootMethod);
        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
            Stmt stmt = (Stmt) unit;
            // Get the operand
            if (stmt instanceof IfStmt) {
                for (Unit u : iCfg.getSuccsOf(stmt)) {
                    logger.info("u: " + u);
                }
                logger.info("post: " + iCfg.getPostdominatorOf(stmt));
            }
        }

    }

}
