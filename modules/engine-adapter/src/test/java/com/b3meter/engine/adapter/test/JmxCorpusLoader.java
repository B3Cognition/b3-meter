/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.adapter.test;

import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JUnit 5 {@code @MethodSource} provider for the JMX corpus located at
 * {@code test-data/jmx-corpus} relative to the project root.
 *
 * <p>The corpus is organised by JMeter version:
 * <pre>
 *   test-data/jmx-corpus/
 *     jmeter-3.x/   (5 files)
 *     jmeter-4.x/   (5 files)
 *     jmeter-5.x/   (5 files)
 *     jmeter-6.x/   (5 files)
 * </pre>
 *
 * <p>Usage in a parameterized test:
 * <pre>{@code
 *   \@ParameterizedTest
 *   \@MethodSource("com.b3meter.engine.adapter.test.JmxCorpusLoader#allCorpusFiles")
 *   void myTest(Path jmxFile) {
 *       // jmxFile is one of the 20 corpus .jmx files
 *   }
 * }</pre>
 *
 * <p>The corpus root is resolved relative to the working directory at test execution
 * time. Gradle sets the working directory to the subproject directory
 * ({@code modules/engine-adapter}), so the path walks up two levels to the project
 * root and then down to {@code test-data/jmx-corpus}.
 */
public final class JmxCorpusLoader {

    /** Expected number of JMX files in the corpus (4 versions × 5 plans each + 1 root-level plan). */
    public static final int EXPECTED_CORPUS_SIZE = 21;

    /**
     * Root of the JMX corpus, resolved relative to the module working directory.
     *
     * <p>During Gradle test execution the CWD is {@code modules/engine-adapter},
     * so {@code ../../test-data/jmx-corpus} resolves to the project-root corpus.
     */
    private static final Path CORPUS_ROOT =
            Path.of("../../test-data/jmx-corpus").toAbsolutePath().normalize();

    private JmxCorpusLoader() {
        // static utility class — no instances
    }

    // -------------------------------------------------------------------------
    // @MethodSource providers
    // -------------------------------------------------------------------------

    /**
     * Returns a stream of {@link Arguments} containing one {@link Path} per
     * {@code .jmx} file found under {@link #CORPUS_ROOT}.
     *
     * <p>Files are returned in lexicographic (depth-first) order, which is
     * deterministic across platforms.
     *
     * @return stream of {@link Arguments}; each wraps a single {@link Path}
     * @throws UncheckedIOException if the corpus directory cannot be walked
     */
    public static Stream<Arguments> allCorpusFiles() {
        try (Stream<Path> walker = Files.walk(CORPUS_ROOT)) {
            List<Path> jmxFiles = walker
                    .filter(p -> p.toString().endsWith(".jmx"))
                    .sorted()
                    .collect(Collectors.toList());
            return jmxFiles.stream().map(Arguments::of);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to walk JMX corpus at: " + CORPUS_ROOT, e);
        }
    }

    // -------------------------------------------------------------------------
    // Introspection helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the number of {@code .jmx} files currently present in the corpus.
     *
     * <p>Useful in test assertions to verify the corpus has not been accidentally
     * shrunk.
     *
     * @return file count; 0 if the corpus directory is empty or not found
     */
    public static int corpusFileCount() {
        if (!Files.exists(CORPUS_ROOT)) {
            return 0;
        }
        try (Stream<Path> walker = Files.walk(CORPUS_ROOT)) {
            return (int) walker
                    .filter(p -> p.toString().endsWith(".jmx"))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to count JMX corpus files at: " + CORPUS_ROOT, e);
        }
    }

    /**
     * Returns the resolved absolute path of the corpus root directory.
     *
     * <p>Useful for diagnostic messages in test failures.
     *
     * @return the corpus root path; never {@code null}
     */
    public static Path corpusRoot() {
        return CORPUS_ROOT;
    }
}
