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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry that maps component names to their implementation classes.
 *
 * <p>On construction (and on each {@link #refresh()} call) all {@link JMeterComponent}
 * implementations discoverable via {@link PluginDiscovery} are instantiated and indexed
 * by their {@link JMeterComponent#getComponentName() component name}.
 *
 * <p>Example mappings after a refresh:
 * <pre>
 *   "HTTPSampler"       -> HTTPSamplerProxy.class
 *   "ThreadGroup"       -> ThreadGroup.class
 *   "ResponseAssertion" -> ResponseAssertion.class
 * </pre>
 *
 * <p>This class is framework-free — pure Java, zero Spring or JMeter-internal imports.
 * Implements Constitution Principle I: Engine-First Decoupling.
 *
 * <p>Thread-safety: {@link #refresh()} replaces the internal map atomically. Concurrent
 * read-only access via {@link #getComponentClass} and {@link #allComponentNames} is safe
 * at any time because reads operate on a stable snapshot. {@link #refresh()} itself
 * is not synchronised — call it from a single coordinating thread during startup or
 * after a plugin is added.
 */
public final class ComponentRegistry {

    /**
     * Immutable snapshot of the current component map.
     *
     * <p>Declared {@code volatile} so a {@link #refresh()} on one thread is
     * immediately visible to threads that call {@link #getComponentClass} or
     * {@link #allComponentNames}.
     */
    private volatile Map<String, Class<?>> componentsByName;

    /**
     * Creates a new registry and performs an initial scan via {@link PluginDiscovery}.
     */
    public ComponentRegistry() {
        this.componentsByName = Collections.emptyMap();
        refresh();
    }

    /**
     * Re-scans all discoverable {@link JMeterComponent} providers and rebuilds the
     * internal name-to-class map.
     *
     * <p>Components registered before this call are replaced entirely by the new scan.
     * A component that was previously discoverable but has since been removed from the
     * class path will no longer appear after {@code refresh()}.
     *
     * <p>If two providers declare the same {@link JMeterComponent#getComponentName()},
     * the last one encountered wins and a warning is silently accepted (first-registered
     * semantics are intentionally not enforced here to allow overriding built-ins with
     * custom implementations).
     */
    public void refresh() {
        List<JMeterComponent> components = PluginDiscovery.discover(JMeterComponent.class);
        Map<String, Class<?>> map = new HashMap<>(components.size() * 2);
        for (JMeterComponent component : components) {
            map.put(component.getComponentName(), component.getClass());
        }
        this.componentsByName = Collections.unmodifiableMap(map);
    }

    /**
     * Returns an unmodifiable view of all component names known to this registry.
     *
     * <p>The returned collection reflects the state of the most recent {@link #refresh()}.
     *
     * @return non-null, possibly empty collection of component name strings
     */
    public Collection<String> allComponentNames() {
        return componentsByName.keySet();
    }

    /**
     * Returns the implementation class registered under {@code name}, if present.
     *
     * @param name the component name to look up; must not be {@code null}
     * @return an {@link Optional} containing the implementation class, or empty if unknown
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public Optional<Class<?>> getComponentClass(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(componentsByName.get(name));
    }
}
