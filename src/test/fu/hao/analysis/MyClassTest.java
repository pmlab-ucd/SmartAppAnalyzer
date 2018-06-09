package test.fu.hao.analysis;

import fu.hao.analysis.Main;
import org.junit.Test;

import static org.junit.Assert.*;

public class MyClassTest {

    @Test
    public void main() throws Exception {
        Main.main(new String[]{"MyClass", "implicitFlow1"});
    }
}