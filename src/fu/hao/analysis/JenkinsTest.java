package fu.hao.analysis;

import static junit.framework.Assert.*;

public class JenkinsTest {

    @org.junit.Before
    public void setUp() throws Exception {
    }

    @org.junit.Test
    public void main() throws Exception {
        Main.main(new String[]{"jenkins-notifier"});
    }
}