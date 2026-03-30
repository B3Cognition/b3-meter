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

import java.time.Instant;

/**
 * API response representation of a test plan.
 *
 * <p>Maps one-to-one to the columns of the {@code test_plans} table that are
 * safe to expose over the public API (soft-delete timestamp is excluded).
 */
public record TestPlanDto(
        String id,
        String name,
        String ownerId,
        String treeData,
        Instant createdAt,
        Instant updatedAt
) {}
