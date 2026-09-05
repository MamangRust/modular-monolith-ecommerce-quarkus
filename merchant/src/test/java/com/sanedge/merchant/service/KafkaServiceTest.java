package com.sanedge.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.chaos.ChaosManager;
import com.sanedge.common.chaos.ChaosPolicy;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class KafkaServiceTest {

    @Mock
    private Vertx vertx;

    @Mock
    private ChaosManager chaosManager;

    private KafkaService kafkaService;

    @BeforeEach
    void setUp() throws Exception {
        kafkaService = new KafkaService();

        java.lang.reflect.Field vertxField = KafkaService.class.getDeclaredField("vertx");
        vertxField.setAccessible(true);
        vertxField.set(kafkaService, vertx);

        java.lang.reflect.Field chaosField = KafkaService.class.getDeclaredField("chaosManager");
        chaosField.setAccessible(true);
        chaosField.set(kafkaService, chaosManager);

        java.lang.reflect.Field bootstrapField = KafkaService.class.getDeclaredField("bootstrapServers");
        bootstrapField.setAccessible(true);
        bootstrapField.set(kafkaService, "localhost:9092");

        java.lang.reflect.Field acksField = KafkaService.class.getDeclaredField("acks");
        acksField.setAccessible(true);
        acksField.set(kafkaService, "1");
    }

    @Test
    void sendMessage_producerNotInitialized_returnsFailure() {
        // producer is null because @PostConstruct was not called
        Uni<Void> result = kafkaService.sendMessage("test-topic", "key", new JsonObject().put("foo", "bar"));

        assertThat(result).isNotNull();
        Throwable failure = null;
        try {
            result.await().indefinitely();
        } catch (Throwable t) {
            failure = t;
        }
        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).contains("Kafka producer not initialized");
    }

    @Test
    void sendMessage_chaosPolicyDropMessage_silentlyCompletes() throws Exception {
        // Inject a mock producer to bypass the null check
        @SuppressWarnings("unchecked")
        io.vertx.kafka.client.producer.KafkaProducer<String, String> producer = mock(
                io.vertx.kafka.client.producer.KafkaProducer.class);
        java.lang.reflect.Field producerField = KafkaService.class.getDeclaredField("producer");
        producerField.setAccessible(true);
        producerField.set(kafkaService, producer);

        // Chaos policy with dropMessage + no latency (synchronous drop path)
        ChaosPolicy policy = mock(ChaosPolicy.class);
        when(policy.isEnabled()).thenReturn(true);
        when(policy.getErrorChance()).thenReturn(1.0);
        when(policy.isDropMessage()).thenReturn(true);
        when(chaosManager.evaluate(any(), any())).thenReturn(policy);

        Uni<Void> result = kafkaService.sendMessage("test-topic", "key", new JsonObject().put("foo", "bar"));
        // Should complete silently (dropMessage, no latency)
        result.await().indefinitely();
        assertThat(result).isNotNull();
    }
}
