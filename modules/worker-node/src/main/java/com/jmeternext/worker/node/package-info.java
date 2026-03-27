/**
 * Worker-node: Spring Boot application that executes test plan fragments.
 *
 * <p>Each worker node receives a {@code RunRequest} from the distributed
 * controller, executes the plan fragment via the engine-adapter, and streams
 * {@code SampleResult} messages back to the controller over gRPC.
 */
package com.jmeternext.worker.node;
