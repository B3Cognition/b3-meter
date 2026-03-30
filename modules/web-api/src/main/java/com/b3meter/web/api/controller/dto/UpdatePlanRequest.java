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
package com.b3meter.web.api.controller.dto;

/**
 * Request body for updating an existing test plan.
 *
 * <p>{@code name} is optional — when null the existing name is preserved.
 * {@code treeData} is optional — when null the existing tree_data is preserved.
 * {@code author} identifies who is making the change (stored in the revision record).
 */
public record UpdatePlanRequest(
        String name,
        String treeData,
        String author
) {}
