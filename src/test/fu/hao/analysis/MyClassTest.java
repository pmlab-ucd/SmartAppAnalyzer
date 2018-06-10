package fu.hao.analysis;

import fu.hao.analysis.InfoflowAnalysis;
import fu.hao.analysis.Main;
import org.junit.BeforeClass;
import org.junit.Test;
import soot.jimple.infoflow.IInfoflow;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.config.ConfigForTest;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.test.junit.JUnitTests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MyClassTest extends JUnitTests {

    protected IInfoflow initInfoflow(boolean useTaintWrapper) {
        Infoflow result = new InfoflowAnalysis("", false, null);
        ConfigForTest testConfig = new ConfigForTest();
        result.setSootConfig(testConfig);
        if (useTaintWrapper) {
            EasyTaintWrapper easyWrapper;
            try {
                easyWrapper = new EasyTaintWrapper();
                result.setTaintWrapper(easyWrapper);
            } catch (IOException e) {
                System.err.println("Could not initialized Taintwrapper:");
                e.printStackTrace();
            }

        }
        return result;
    }

    @BeforeClass
    public static void setUp() throws IOException {
        final String sep = System.getProperty("path.separator");
        File f = new File(".");
        File testSrc1 = new File(f, "out" + File.separator + "test" + File.separator + "SmartAppAnalyzer");

        if (!testSrc1.exists()) {
            fail("Test aborted - none of the test sources are available");
        }


        appPath = testSrc1.getCanonicalPath();
        libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";

        sources = new ArrayList<String>();
        sources.add("<MyClass: boolean booleanSource()>");
        sinks = new ArrayList<String>();
        sinks.add("<MyClass: void booleanSink(boolean)>");
    }

    @Test
    public void main() throws Exception {

        List<String> epoints = new ArrayList<String>();
        epoints.add("<MyClass: void implicitFlow1()>");

        IInfoflow infoflow = initInfoflow();
        infoflow.computeInfoflow(appPath, libPath, epoints, sources, sinks);
        checkInfoflow(infoflow, 1);

//        InfoflowAnalysis.entry_points.add("<MyClass: void implicitFlow1()>");
//        InfoflowAnalysis.sources.add("<MyClass: boolean booleanSource()>");
//        InfoflowAnalysis.sinks.add("<MyClass: boolean booleanSink(boolean)>");
//        Main.main(new String[]{"MyClass", "implicitFlow2"});
    }
}