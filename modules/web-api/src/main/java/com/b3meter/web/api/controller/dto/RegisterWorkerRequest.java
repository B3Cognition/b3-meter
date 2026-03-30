package com.jmeternext.web.api.controller.dto;

/**
 * Request body for registering a new worker node.
 *
 * @param hostname hostname or IP address of the worker; must not be blank
 * @param port     RMI port the worker listens on; must be in range 1–65535
 */
public record RegisterWorkerRequest(
        String hostname,
        Integer port
) {}
