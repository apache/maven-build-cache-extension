/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.buildcache;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChainedListenerTest {

    @Test
    void nullDelegateDoesNotThrow() {
        ChainedListener listener = new ChainedListener(null);
        ExecutionEvent event = mock(ExecutionEvent.class);

        assertDoesNotThrow(() -> {
            listener.projectDiscoveryStarted(event);
            listener.sessionStarted(event);
            listener.sessionEnded(event);
            listener.projectSkipped(event);
            listener.projectStarted(event);
            listener.projectSucceeded(event);
            listener.projectFailed(event);
            listener.forkStarted(event);
            listener.forkSucceeded(event);
            listener.forkFailed(event);
            listener.mojoSkipped(event);
            listener.mojoStarted(event);
            listener.mojoSucceeded(event);
            listener.mojoFailed(event);
            listener.forkedProjectStarted(event);
            listener.forkedProjectSucceeded(event);
            listener.forkedProjectFailed(event);
        });
    }

    @Test
    void nonNullDelegateReceivesCalls() {
        ExecutionListener delegate = mock(ExecutionListener.class);
        ChainedListener listener = new ChainedListener(delegate);
        ExecutionEvent event = mock(ExecutionEvent.class);

        listener.mojoStarted(event);

        verify(delegate).mojoStarted(event);
    }

    @Test
    void chainedListenerReceivesCalls() {
        ChainedListener listener = new ChainedListener(null);
        ExecutionListener chained = mock(ExecutionListener.class);
        listener.chainListener(chained);
        ExecutionEvent event = mock(ExecutionEvent.class);

        listener.mojoStarted(event);

        verify(chained).mojoStarted(event);
    }
}
