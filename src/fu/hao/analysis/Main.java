/*
 * Warning:
 * This can only be executed correctly when using SOOTCLASSES
 * and put the /bin/ as External Class folder suggested as
 * http://stackoverflow.com/questions/20282481/loading-java-class-files-for-soot-dynamically-in-eclipse.
 * Not working anymore if use soot-trunk.jar as lib
 */

package fu.hao.analysis;

import soot.jimple.infoflow.Infoflow;

/**
 * The main entry of analysis.
 */
public class Main extends Infoflow {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java Main class_name_to_analyse");
            System.exit(0);
        }

        InfoflowAnalysis infoflowAnalysis = new InfoflowAnalysis(args);
    }

}
