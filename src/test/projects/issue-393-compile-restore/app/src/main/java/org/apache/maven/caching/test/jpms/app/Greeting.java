package org.apache.maven.caching.test.jpms.app;

public final class Greeting {

    private Greeting() {
        // utility
    }

    public static String message() {
        return "hello from module";
    }
}
