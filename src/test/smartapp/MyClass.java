import java.util.Random;

/**
 * Created by hfu on 8/22/2017.
 */

public class MyClass {

    static boolean status = false;

    private void myMethod() {
        int x, a, b;
        x = 30;
        a = x - 1;
        b = x - 2;
        if (x > 0) {
            System.out.print(a * b - x);
            x = x - 1;
            System.err.println(x);
        } else if (a == 3) {
            System.out.print(a * b);
        } else {
            int w = a + 2;
            System.out.println(w);
        }
        int t = 4;
        System.out.println(t + 8);
    }

    private void implicitFlow1() {
        int env = source();
        if (env == 42) {
            status = booleanSource();
            implicitFlow2();
        }
    }

    private void implicitFlow2() {
        if (status) {
            booleanSink(status);
            System.out.println("implicit");
        }
    }

    private void testTaintForwardVar() {
        boolean x = booleanSource();
        boolean y = x, k = false;
        x = k;
        booleanSink(x);
        System.out.print("\n");
    }

    private boolean booleanSource() {
        return (new Random().nextInt() == 42);
    }

    private void booleanSink(boolean x) {
        System.out.println(x);
    }

    private int source() {
        // ultimate answer of the universe
        return new Random().nextInt();
    }

    private void sink(int x) {
        System.out.print(x);
    }

    public static void main(String[] args) {
        MyClass mc = new MyClass();
        mc.implicitFlow1(); // myMethod();
        mc.testTaintForwardVar();
    }
}
