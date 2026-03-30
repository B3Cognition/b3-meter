/**
 * Web-api: Spring Boot REST + SSE backend.
 *
 * <p>This module exposes HTTP endpoints consumed by the React frontend.
 * It depends only on {@link com.jmeternext.engine.service} interfaces — never
 * on {@code engine-adapter} directly. This architectural constraint is enforced
 * by ArchUnit rules (see T002).
 */
package com.jmeternext.web.api;
