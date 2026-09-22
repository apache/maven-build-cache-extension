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

import org.apache.commons.lang3.StringUtils;

import static java.util.Objects.requireNonNull;

/**
 * A named partition of the cache for one project checksum. A build reads its input zones in order and
 * restores from the first hit, then saves its result into every output zone that lacks a usable entry,
 * so several final states (e.g. one per CI test group) can coexist under the same checksum.
 *
 * @author Réda Housni Alaoui
 */
public class Zone {

    private final String name;

    public Zone(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Zone name cannot be blank");
        }
        this.name = requireNonNull(name);
    }

    public String value() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Zone)) {
            return false;
        }

        Zone zone = (Zone) o;
        return name.equals(zone.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
