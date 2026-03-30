/**
 * Engine-service: core API interfaces for jmeter-next.
 *
 * <p>This package defines the contract between the test engine and any consuming
 * layer (UI, REST API, distributed controller). No concrete implementations
 * belong here — only interfaces and value objects.
 *
 * <p>Key interfaces to be implemented in subsequent tasks:
 * <ul>
 *   <li>{@code UIBridge} — callbacks from the engine to the UI layer</li>
 *   <li>{@code TestRunContext} — immutable context carrying run configuration</li>
 * </ul>
 *
 * @see <a href="https://github.com/jmeter-next/jmeter-next">jmeter-next</a>
 */
package com.jmeternext.engine.service;
