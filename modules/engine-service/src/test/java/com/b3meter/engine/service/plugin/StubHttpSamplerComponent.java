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
 * Test-only stub simulating an HTTP Sampler component for ServiceLoader discovery tests.
 *
 * <p>Declared in
 * {@code META-INF/services/com.b3meter.engine.service.plugin.JMeterComponent}
 * so it is discovered by {@link PluginDiscovery} during testing.
 */
public final class StubHttpSamplerComponent implements JMeterComponent {

    /** Component name used as the lookup key in {@link ComponentRegistry}. */
    public static final String NAME     = "HTTPSampler";

    /** Category string returned by this stub. */
    public static final String CATEGORY = "sampler";

    @Override
    public String getComponentName() {
        return NAME;
    }

    @Override
    public String getComponentCategory() {
        return CATEGORY;
    }
}
