/*
 * Warning:
 * This can only be executed correctly when using SOOTCLASSES
 * and put the /bin/ as External Class folder suggested as
 * http://stackoverflow.com/questions/20282481/loading-java-class-files-for-soot-dynamically-in-eclipse.
 * Not working anymore if use soot-trunk.jar as lib
 */

package fu.hao.analysis;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import fu.hao.analysis.cg.CallGraphResolver;
import fu.hao.utils.Settings;
//import heros.InterproceduralCFG;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.options.Options;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.SootMethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final String TAG = "MAIN";
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    protected static SootClass initializeSoot(String className) {
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

        return c;
    }

    public static void main(String[] args) throws Exception {
        //args = new String[] {"C:\\Users\\hfu\\Documents\\myclasses.jar", ""};

        //if (args.length == 0) {
        //System.out.println("Usage: java RunLiveAnalysis class_to_analyse");
        //System.exit(0);
        //}

        //System.out.println("Setting classpath to: " + args[0]);
        //Scene.v().setSootClassPath(args[0]);
        Settings.setAppName("simple-auto-lock-door");
        Settings.setOutputDirectory("output");
        Settings.setLogLevel(0);

        SootClass tgtClass = initializeSoot("simple-auto-lock-door");

        //Options.v().set_on_the_fly(true);
        //Options.v().set_whole_program(true);
        //Options.v().set_allow_phantom_refs(true);
        //Options.v().setPhaseOption("cg.spark", "on");

        // do not merge variables (causes problems with PointsToSets)
        Options.v().setPhaseOption("jb.ulp", "off");


        // To cope with broken APK files, we convert all classes that are still
        // dangling after resolution into phantoms
        for (SootClass sc : Scene.v().getClasses())
            if (sc.resolvingLevel() == SootClass.DANGLING) {
                sc.setResolvingLevel(SootClass.BODIES);
                sc.setPhantomClass();
            }
        // 载入MyClass类
        //SootClass tgtClass = Scene.v().loadClassAndSupport("simple-auto-lock-door");
        //tgtClass.declaresMethodByName("dummyMain");
        // 把它作为我们要分析的类
        //tgtClass.setApplicationClass();
        //Options.v().set_main_class(tgtClass.getMethodByName("doorOpen").getSignature());

        PackManager.v().getPack("wjpp").apply();
        PackManager.v().getPack("cg").apply();

        //PackManager.v().runPacks();

        Map<Integer, String> callSites = CallGraphResolver.getCallSites(tgtClass);
        System.out.println(callSites);
        CallGraph callGraph = Scene.v().getCallGraph(); //new CallGraph();


        // Scene.v().setCallGraph(callGraph);


        logger.info(String.valueOf(callGraph.size()));

        // 找到它的myMethod函数
        //SootMethod method = tgtClass.getMethodByName("checkMotion");
        for (SootMethod method : tgtClass.getMethods()) {
            if (method.getName().contains("CallSiteArray") || method.getName().matches("run")) {
                continue;
            }

            System.out.println("---------------------------------------");
            System.out.println("method: " + method);
            // 获得它的函数体
            Body body = method.retrieveActiveBody();
            // 生成函数的control flow graph
            UnitGraph cfg = new ExceptionalUnitGraph(body);
            // 执行我们的分析
            //TaintForwardAnalysis.TaintForwardVarAnalysis ta = new TaintForwardAnalysis.TaintForwardVarAnalysis(cfg);
            // iterate over the results
            for (Unit unit : cfg) {
                //System.out.println(unit);
                //List<Local> before = ta.getLiveLocalsBefore(unit);
                //List<Local> after = ta.getLiveLocalsAfter(unit);
                UnitPrinter up = new NormalUnitPrinter(body);
                up.setIndent("");

                //System.out.println("---------------------------------------");
                unit.toString(up);
                System.out.println(up.output() + ", " + up.hashCode());
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    stmt.getInvokeExpr().getMethod();
                }

            /*
            MethodSummary methodSummary = new MethodSummary(method, callSites);
            for (String name : methodSummary.getName2stmts().keySet()) {
                if (name.contains("minutesLater")) {
                    System.out.println("stmt: " + methodSummary.getName2stmts().get(name));
                    List<Stmt> slice = slicing(method, methodSummary.getName2stmts().get(name));

                    for (Stmt stmt : slice) {
                        System.out.println(stmt);
                    }
                    methodSummary.interpretation(slice);
                }
            } */

            /*
            if (!before.isEmpty()) {
                if (unit.toString().contains("sink")) {
                    System.out.println("found a sink!");
                }
            }
            System.out.print("Taint in: {");
            sep = "";
            for (Local l : before) {
                System.out.print(sep);
                System.out.print(l.getName() + ": " + l.getType());
                sep = ", ";
            }
            System.out.println("}");
            System.out.print("Taint out: {");
            sep = "";
            for (Local l : after) {
                System.out.print(sep);
                System.out.print(l.getName() + ": " + l.getType());
                sep = ", ";
            }
            System.out.println("}");
            System.out.println("---------------------------------------");*/
            }

            CallGraphResolver.addCallEdges(callGraph, method, callSites);
        }
        //InterproceduralCFG<Unit, SootMethod> icfg = new JimpleBasedInterproceduralCFG();

    }

}