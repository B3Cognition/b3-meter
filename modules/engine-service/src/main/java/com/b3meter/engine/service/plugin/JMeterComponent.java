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
package com.b3meter.engine.service.plugin;

/**
 * Marker interface that all b3meter components implement.
 *
 * <p>This interface serves as the {@link java.util.ServiceLoader} service type for
 * plugin discovery. Any component that should be discovered at runtime must:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Declare itself in {@code META-INF/services/com.b3meter.engine.service.plugin.JMeterComponent}</li>
 * </ol>
 *
 * <p>This interface is framework-free — no Spring, no JMeter-internal imports.
 * Implements Constitution Principle I: Engine-First Decoupling.
 */
public interface JMeterComponent {

    /**
     * Returns the canonical name of this component as used in test plan serialisation
     * (e.g. {@code "HTTPSampler"}, {@code "ThreadGroup"}, {@code "ResponseAssertion"}).
     *
     * <p>Names must be unique within a category. {@link ComponentRegistry} uses this
     * value as the lookup key.
     *
     * @return non-null, non-blank component name
     */
    String getComponentName();

    /**
     * Returns the functional category of this component.
     *
     * <p>Well-known categories: {@code sampler}, {@code controller}, {@code assertion},
     * {@code timer}, {@code listener}, {@code config}, {@code pre-processor},
     * {@code post-processor}.
     *
     * @return non-null, non-blank category string
     */
    String getComponentCategory();
}
