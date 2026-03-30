package com.jmeternext.web.api.config;

import com.jmeternext.engine.adapter.EngineServiceImpl;
import com.jmeternext.engine.adapter.NoOpUIBridge;
import com.jmeternext.engine.adapter.http.Hc5HttpClientFactory;
import com.jmeternext.engine.service.EngineService;
import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleBucketConsumer;
import com.jmeternext.engine.service.SampleStreamBroker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring configuration for engine-layer collaborators.
 *
 * <p>Provides an in-process {@link SampleStreamBroker} implementation and wires
 * the real {@link EngineService} backed by {@link EngineServiceImpl}. The broker is a
 * single-node, in-memory fan-out implementation sufficient for desktop mode.
 */
@Configuration
public class EngineConfig {

    /**
     * Creates the in-process {@link SampleStreamBroker} bean.
     *
     * <p>Backed by {@link CopyOnWriteArrayList} per run for thread-safe subscriber
     * fan-out. A failing consumer is logged and skipped; delivery continues to the
     * remaining consumers.
     *
     * @return a new singleton broker instance
     */
    @Bean
    public SampleStreamBroker sampleStreamBroker() {
        return new InProcessSampleStreamBroker();
    }

    /**
     * Creates the real {@link EngineService} bean backed by {@link EngineServiceImpl}.
     *
     * <p>Uses {@link Hc5HttpClientFactory} for real HTTP execution with configurable
     * connection pool settings from {@code application.yml}. The factory is not
     * exposed as a bean because it is an implementation detail of the engine
     * adapter layer; the bean is closed when the Spring context shuts down.
     *
     * @param broker          the broker for publishing sample buckets; injected by Spring
     * @param maxConnections  maximum total connections (from jmeter.http.maxConnections)
     * @param maxPerRoute     maximum connections per route (from jmeter.http.maxPerRoute)
     * @return a singleton {@link EngineServiceImpl} instance
     */
    @Bean
    public EngineService engineService(
            SampleStreamBroker broker,
            @Value("${jmeter.http.maxConnections:2000}") int maxConnections,
            @Value("${jmeter.http.maxPerRoute:200}") int maxPerRoute) {
        Hc5HttpClientFactory httpClientFactory = new Hc5HttpClientFactory(maxConnections, maxPerRoute);
        return new EngineServiceImpl(broker, httpClientFactory, NoOpUIBridge.INSTANCE);
    }

    // -------------------------------------------------------------------------
    // Inner implementation — avoids engine-adapter dependency
    // -------------------------------------------------------------------------

    /**
     * In-process {@link SampleStreamBroker} backed by {@link CopyOnWriteArrayList}.
     *
     * <p>This implementation mirrors {@code InMemorySampleStreamBroker} from
     * {@code engine-adapter} but lives in {@code web-api} so the module boundary
     * is preserved.
     */
    private static final class InProcessSampleStreamBroker implements SampleStreamBroker {

        private static final Logger LOG =
                Logger.getLogger(InProcessSampleStreamBroker.class.getName());

        private final ConcurrentHashMap<String, CopyOnWriteArrayList<SampleBucketConsumer>>
                consumersByRun = new ConcurrentHashMap<>();

        @Override
        public void subscribe(String runId, SampleBucketConsumer consumer) {
            Objects.requireNonNull(runId,    "runId must not be null");
            Objects.requireNonNull(consumer, "consumer must not be null");
            consumersByRun
                    .computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>())
                    .addIfAbsent(consumer);
        }

        @Override
        public void unsubscribe(String runId, SampleBucketConsumer consumer) {
            Objects.requireNonNull(runId,    "runId must not be null");
            Objects.requireNonNull(consumer, "consumer must not be null");
            List<SampleBucketConsumer> consumers = consumersByRun.get(runId);
            if (consumers != null) {
                consumers.remove(consumer);
            }
        }

        @Override
        public void publish(String runId, SampleBucket bucket) {
            Objects.requireNonNull(runId,  "runId must not be null");
            Objects.requireNonNull(bucket, "bucket must not be null");
            List<SampleBucketConsumer> consumers = consumersByRun.get(runId);
            if (consumers == null || consumers.isEmpty()) {
                return;
            }
            for (SampleBucketConsumer consumer : consumers) {
                try {
                    consumer.onBucket(bucket);
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING,
                            "SampleBucketConsumer threw an exception for runId={0}; delivery continues",
                            new Object[]{runId});
                    LOG.log(Level.WARNING, "Consumer exception detail", ex);
                }
            }
        }
    }
}
