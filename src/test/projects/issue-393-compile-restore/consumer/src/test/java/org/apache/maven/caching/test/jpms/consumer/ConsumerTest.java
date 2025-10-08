package org.apache.maven.caching.test.jpms.consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumerTest {

    @Test
    void messageIsProvidedByUpstreamModule() {
        assertEquals("hello from module", Consumer.message());
    }
}
