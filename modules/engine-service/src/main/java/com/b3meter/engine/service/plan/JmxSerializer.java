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
package com.b3meter.engine.service.plan;

import java.util.Objects;

/**
 * Converts a {@link PlanNode} tree back to JMeter-compatible JMX XML.
 *
 * <p>This is a named public entry-point for the serialization path introduced
 * in T014 (spec 009-quality-jmx-parsing). It delegates to {@link JmxWriter},
 * which is the existing StAX/StringBuilder-based serializer for {@link PlanNode}
 * trees.
 *
 * <p>This class is framework-free (no Spring imports) and stateless — safe
 * for concurrent use (see NFR-009.004).
 *
 * @see JmxWriter
 * @see PlanNodeSerializer
 */
public final class JmxSerializer {

    private JmxSerializer() {
        // utility class — not instantiable
    }

    /**
     * Converts the {@link PlanNode} tree rooted at {@code root} to JMX XML.
     *
     * <p>The output is structurally equivalent to the original JMX for all
     * supported property types. It is valid JMeter 5.x/6.x JMX and will be
     * accepted by {@link JmxTreeWalker#parse(String)}.
     *
     * @param root the root plan node; must not be {@code null}
     * @return well-formed JMX XML string; never {@code null}
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static String toJmx(PlanNode root) {
        Objects.requireNonNull(root, "root must not be null");
        return JmxWriter.write(root);
    }
}
