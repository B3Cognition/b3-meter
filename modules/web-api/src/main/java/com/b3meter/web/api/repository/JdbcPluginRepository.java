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
package com.b3meter.web.api.repository;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link PluginRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Data is held in memory for the lifetime of the application process.
 */
@Repository
public class JdbcPluginRepository implements PluginRepository {

    private final ConcurrentHashMap<String, PluginEntity> plugins = new ConcurrentHashMap<>();

    @Override
    public PluginEntity create(PluginEntity plugin) {
        plugins.put(plugin.id(), plugin);
        return plugin;
    }

    @Override
    public Optional<PluginEntity> findById(String id) {
        return Optional.ofNullable(plugins.get(id));
    }

    @Override
    public List<PluginEntity> findAll() {
        return plugins.values().stream()
                .sorted(Comparator.comparing(
                        PluginEntity::installedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public void updateStatus(String id, String status) {
        plugins.computeIfPresent(id, (k, existing) -> new PluginEntity(
                existing.id(),
                existing.name(),
                existing.version(),
                existing.jarPath(),
                status,
                existing.installedBy(),
                existing.installedAt()
        ));
    }

    @Override
    public boolean delete(String id) {
        return plugins.remove(id) != null;
    }
}
