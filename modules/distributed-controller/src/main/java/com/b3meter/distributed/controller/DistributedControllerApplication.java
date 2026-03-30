package com.jmeternext.distributed.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the distributed controller node.
 *
 * <p>This application manages the lifecycle of distributed test runs:
 * it registers worker nodes, fans out test plan fragments, and aggregates
 * result streams from workers via gRPC.
 */
@SpringBootApplication
public class DistributedControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedControllerApplication.class, args);
    }
}
