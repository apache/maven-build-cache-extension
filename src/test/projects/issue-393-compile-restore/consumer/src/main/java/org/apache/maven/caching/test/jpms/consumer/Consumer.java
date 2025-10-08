package org.apache.maven.caching.test.jpms.consumer;

import org.apache.maven.caching.test.jpms.app.Greeting;

public final class Consumer {

    private Consumer() {
        // utility
    }

    public static String message() {
        return Greeting.message();
    }
}
