package org.apache.maven.buildcache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostnameResolverTest {

    @Test
    void testResolve() {
        assertNotNull(HostnameResolver.resolve());
        assertNotEquals("unknown", HostnameResolver.resolve());
    }

}