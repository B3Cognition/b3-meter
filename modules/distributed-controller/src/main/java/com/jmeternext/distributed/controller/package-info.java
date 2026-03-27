/**
 * Distributed-controller: Spring Boot application that coordinates worker nodes.
 *
 * <p>The controller receives test run requests, splits the virtual user load
 * across available worker nodes, and aggregates streamed {@code SampleResult}
 * messages from each worker into a unified result set.
 */
package com.jmeternext.distributed.controller;
