package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.notification.event.PasswordChangedEvent;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PasswordChangedConsumerTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    MockMailbox mailbox;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mailbox.clear();
    }

    @Test
    void testPasswordChangedEmailSent() throws Exception {
        InMemorySource<String> source = connector.source("password-changed");

        String payload = objectMapper.writeValueAsString(new PasswordChangedEvent(
                1L,
                "pwd-changed@kitti.es",
                "Test",
                "9.9.9.9",
                LocalDateTime.of(2026, 5, 19, 14, 30),
                "jwt-reset-abc",
                LocalDateTime.of(2026, 5, 20, 14, 30)
        ));

        source.send(payload);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(mailbox.getMailsSentTo("pwd-changed@kitti.es").isEmpty());
            var mail = mailbox.getMailsSentTo("pwd-changed@kitti.es").get(0);
            assertEquals("Tu contraseña en Kitties ha sido cambiada 🐾", mail.getSubject());
            assertTrue(mail.getHtml().contains("jwt-reset-abc"));
        });
    }

    @Test
    void testPasswordChangedEmailContainsRollbackLink() throws Exception {
        InMemorySource<String> source = connector.source("password-changed");

        String payload = objectMapper.writeValueAsString(new PasswordChangedEvent(
                2L,
                "link-test@kitti.es",
                "LinkTest",
                "1.2.3.4",
                LocalDateTime.of(2026, 5, 19, 14, 30),
                "my-unique-reset-jwt",
                LocalDateTime.of(2026, 5, 20, 14, 30)
        ));

        source.send(payload);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(mailbox.getMailsSentTo("link-test@kitti.es").isEmpty());
            var mail = mailbox.getMailsSentTo("link-test@kitti.es").get(0);
            assertTrue(mail.getHtml().contains("password-reset?token=my-unique-reset-jwt"));
            assertTrue(mail.getHtml().contains("1.2.3.4"));
        });
    }
}
