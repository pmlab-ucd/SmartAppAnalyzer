/*
 * Warning:
 * This can only be executed correctly when using SOOTCLASSES　
 * and put the /bin/ as External Class folder suggested as 
 * http://stackoverflow.com/questions/20282481/loading-java-class-files-for-soot-dynamically-in-eclipse.
 * Not working anymore if use soot-trunk.jar as lib
 */

package fu.hao.analysis;

import java.io.File;
import java.util.List;

import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.FlowSet;
import groovy.lang.Script;

import static soot.SootClass.SIGNATURES;

public class Main {
    public static void main(String[] args) {
        args = new String[] {"C:\\Users\\hfu\\Documents\\myclasses.jar", ""};

        if (args.length == 0) {
            System.out.println("Usage: java RunLiveAnalysis class_to_analyse");
            System.exit(0);
        }

        //System.out.println("Setting classpath to: " + args[0]);
        //Scene.v().setSootClassPath(args[0]);

        String sep = File.separator;
        String pathSep = File.pathSeparator;
        String path = System.getProperty("java.home") + sep + "lib" + sep
                + "rt.jar";
        path += pathSep + "." + sep + "out\\production\\SmartAppAnalyzer";


        path += pathSep + "C:\\Users\\hfu\\IdeaProjects\\SmartAppAnalyzer\\libs/groovy-all-2.2.0-beta-1.jar";
        path += pathSep + args[0];
        Options.v().set_soot_classpath(path);


        // 载入MyClass类
        SootClass tgtClass = Scene.v().loadClassAndSupport("myclasses");
        // 把它作为我们要分析的类
        tgtClass.setApplicationClass();
        Scene.v().loadNecessaryClasses();


            // 找到它的myMethod函数
            //SootMethod method = tgtClass.getMethodByName("checkMotion");
        for (SootMethod method : tgtClass.getMethods()) {
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
                System.out.println(up.output());
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
        }
    }

}