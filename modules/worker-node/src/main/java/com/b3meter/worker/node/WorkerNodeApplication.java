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
package com.b3meter.worker.node;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring Boot entry point for the worker node process.
 *
 * <p>Starts the gRPC server on the configured port and blocks until
 * the JVM shuts down. The worker accepts test plans from the controller,
 * executes them via the engine, and streams results back.
 */
@SpringBootApplication
public class WorkerNodeApplication {

    private static final Logger LOG = Logger.getLogger(WorkerNodeApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication.run(WorkerNodeApplication.class, args);
    }

    @Bean
    CommandLineRunner startGrpcServer(
            org.springframework.core.env.Environment env) {
        return args -> {
            int grpcPort = Integer.parseInt(
                    env.getProperty("grpc.port",
                            System.getenv().getOrDefault("GRPC_PORT", "9090")));
            String workerName = env.getProperty("worker.name",
                    System.getenv().getOrDefault("WORKER_NAME", "worker-" + grpcPort));

            LOG.log(Level.INFO, "Starting worker ''{0}'' with gRPC on port {1}",
                    new Object[]{workerName, grpcPort});

            WorkerGrpcServer grpcServer = new WorkerGrpcServer();
            grpcServer.start(grpcPort);

            LOG.log(Level.INFO, "Worker ''{0}'' ready — gRPC listening on port {1}",
                    new Object[]{workerName, grpcPort});

            // Block the main thread to keep the JVM alive
            grpcServer.awaitTermination();
        };
    }
}
