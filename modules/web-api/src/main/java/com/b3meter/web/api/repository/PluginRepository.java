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

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link PluginEntity}.
 *
 * <p>Plugins are never hard-deleted through this interface; {@link #delete}
 * removes the row only when an admin explicitly uninstalls the plugin.
 */
public interface PluginRepository {

    /**
     * Persists a new plugin row and returns the stored entity.
     *
     * @param plugin the plugin to create; must not be null
     * @return the created entity
     */
    PluginEntity create(PluginEntity plugin);

    /**
     * Returns the plugin with the given {@code id}, or empty if it does not exist.
     *
     * @param id the plugin identifier; must not be null
     * @return the plugin, or empty
     */
    Optional<PluginEntity> findById(String id);

    /**
     * Returns all plugins in the database ordered by installation time (oldest first).
     *
     * @return list of all plugins, possibly empty
     */
    List<PluginEntity> findAll();

    /**
     * Updates the {@code status} column of the plugin with the given {@code id}.
     *
     * @param id     the plugin identifier; must not be null
     * @param status the new status value; must not be null
     */
    void updateStatus(String id, String status);

    /**
     * Permanently removes the plugin row with the given {@code id}.
     *
     * @param id the plugin identifier; must not be null
     * @return {@code true} if a row was deleted, {@code false} if not found
     */
    boolean delete(String id);
}
