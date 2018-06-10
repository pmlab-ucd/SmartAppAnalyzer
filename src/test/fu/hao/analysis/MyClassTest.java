package fu.hao.analysis;

import fu.hao.analysis.SmartAppTest;
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

public class MyClassTest extends SmartAppTest {

    @Test
    public void main()  {
        sources.add("<MyClass: boolean booleanSource()>");
        sinks.add("<MyClass: void booleanSink(boolean)>");

        List<String> epoints = new ArrayList<String>();
        epoints.add("<MyClass: void implicitFlow1()>");

        IInfoflow infoflow = initInfoflow();
        infoflow.computeInfoflow(appPath, libPath, epoints, sources, sinks);
        checkInfoflow(infoflow, 1);
    }
}