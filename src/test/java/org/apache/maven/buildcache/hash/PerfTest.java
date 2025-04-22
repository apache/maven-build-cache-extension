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
package org.apache.maven.buildcache.hash;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
public class PerfTest {

    @State(Scope.Benchmark)
    public static class HashState {
        List<Path> paths;

        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            try (Stream<Path> stream = Files.walk(Paths.get(System.getProperty("user.dir")))) {
                paths = stream.filter(p -> p.getFileName().toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }
        }
    }

    String doTest(HashFactory hashFactory, HashState state) throws IOException {
        HashAlgorithm hash = hashFactory.createAlgorithm();
        StringBuilder sb = new StringBuilder();
        for (Path path : state.paths) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(hash.hash(path));
        }
        return sb.toString();
    }

    @Benchmark
    public String checkSHA1(HashState state) throws IOException {
        return doTest(HashFactory.SHA1, state);
    }

    @Benchmark
    public String checkSHA256(HashState state) throws IOException {
        return doTest(HashFactory.SHA256, state);
    }

    @Benchmark
    public String checkXX(HashState state) throws IOException {
        return doTest(HashFactory.XX, state);
    }

    @Benchmark
    public String checkXXMM(HashState state) throws IOException {
        return doTest(HashFactory.XXMM, state);
    }

    @Benchmark
    public String checkMETRO(HashState state) throws IOException {
        return doTest(HashFactory.METRO, state);
    }

    @Benchmark
    public String checkMETROMM(HashState state) throws IOException {
        return doTest(HashFactory.METRO_MM, state);
    }

    /*
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object.
     * @throws org.openjdk.jmh.runner.RunnerException if any.
     */
    public static void main(String... args) throws RunnerException {
        Options opts = new OptionsBuilder()
                .measurementIterations(3)
                .measurementTime(TimeValue.milliseconds(3000))
                .forks(1)
                .build();
        new Runner(opts).run();
    }
}
