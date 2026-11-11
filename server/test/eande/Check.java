package eande;

import java.util.Objects;

/**
 * A tiny test runner, so the project needs no test library.
 *
 * Each test is a name and a block of code. The block passes if it finishes,
 * and fails if any check inside it throws. Every test runs even after one
 * fails, and AllTests prints a summary at the end.
 */
final class Check {
    private Check() {}

    interface Body {
        void run() throws Exception;
    }

    static int passed = 0;
    static int failed = 0;

    static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  ok    " + name);
        } catch (Throwable error) {
            failed++;
            System.out.println("  FAIL  " + name);
            System.out.println("        " + error);
        }
    }

    static void section(String name) {
        System.out.println();
        System.out.println(name);
    }

    static void isTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but got <" + actual + ">");
        }
    }

    static void contains(String text, String part) {
        if (text == null || !text.contains(part)) {
            throw new AssertionError("expected to find <" + part + "> in <" + shorten(text) + ">");
        }
    }

    static void lacks(String text, String part) {
        if (text != null && text.contains(part)) {
            throw new AssertionError("expected NOT to find <" + part + "> in <" + shorten(text) + ">");
        }
    }

    static void fails(Body body) {
        try {
            body.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("expected an error, but it worked");
    }

    private static String shorten(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
