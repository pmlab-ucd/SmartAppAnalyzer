/**
 * Created by hfu on 8/22/2017.
 */

public class MyClass {
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

    private void testTaintForwardVar() {
        int x = source();
        int y = x, k = 3;
        x = k;
        sink(y);
        System.out.print("\n");
    }

    private int source() {
        // ultimate answer of the universe
        return 42;
    }

    private void sink(int x) {
        System.out.print(x);
    }

    public static void main(String[] args) {
        MyClass mc = new MyClass();
        mc.myMethod();
    }
}
