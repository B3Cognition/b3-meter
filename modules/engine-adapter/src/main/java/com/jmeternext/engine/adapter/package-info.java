/**
 * Engine-adapter: anti-corruption layer between legacy JMeter internals
 * and the clean jmeter-next engine-service interfaces.
 *
 * <p>Classes in this package adapt the existing {@code StandardJMeterEngine}
 * and related types to the {@link com.jmeternext.engine.service.UIBridge} and
 * {@link com.jmeternext.engine.service.TestRunContext} contracts.
 */
package com.jmeternext.engine.adapter;
