package fu.hao.analysis;

import org.junit.BeforeClass;
import soot.jimple.infoflow.IInfoflow;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.config.ConfigForTest;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.test.junit.JUnitTests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.fail;

public class SmartAppTest extends JUnitTests {
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
        File testSrc1 = new File(f, "out" + File.separator + "production" + File.separator + "SmartAppAnalyzer");

        if (!testSrc1.exists()) {
            fail("Test aborted - none of the test sources are available");
        }

        appPath = testSrc1.getCanonicalPath();
        libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";

        sources = new ArrayList<String>();
        sources.add("<YouLeftTheDoorOpen: boolean booleanSource()>");
        sinks = new ArrayList<String>();
        sinks.add("<YouLeftTheDoorOpen: void booleanSink(boolean)>");
    }
}
