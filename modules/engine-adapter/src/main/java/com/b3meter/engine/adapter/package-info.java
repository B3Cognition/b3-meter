/**
 * Engine-adapter: anti-corruption layer between legacy JMeter internals
 * and the clean b3meter engine-service interfaces.
 *
 * <p>Classes in this package adapt the existing {@code StandardJMeterEngine}
 * and related types to the {@link com.b3meter.engine.service.UIBridge} and
 * {@link com.b3meter.engine.service.TestRunContext} contracts.
 */
package com.b3meter.engine.adapter;
