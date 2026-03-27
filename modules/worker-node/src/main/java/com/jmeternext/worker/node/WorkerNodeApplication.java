package com.jmeternext.worker.node;

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
