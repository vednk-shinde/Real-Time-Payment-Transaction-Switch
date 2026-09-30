package com.paymentswitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentswitch.model.AuditRecord;
import com.paymentswitch.model.TransactionStatus;
import com.paymentswitch.repository.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full stack: real JWT login against seeded BCrypt users, H2 via JPA, and an
 * embedded Kafka broker carrying events from the producer to the audit
 * consumer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = "transaction-events")
@DirtiesContext
class TransactionSwitchIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuditRecordRepository auditRecords;

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private static String body(String key, String card, String amount) {
        return """
                {"idempotencyKey":"%s","cardNumber":"%s","merchantId":"MERCHANT-42","acquirerId":"ACQUIRER-7",
                 "amount":%s,"currency":"USD","channel":"ECOMMERCE"}
                """.formatted(key, card, amount);
    }

    private JsonNode submit(String token, String body, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/transactions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void approvedTransactionIsPersistedAndItsFullLifecycleFlowsThroughKafka() throws Exception {
        String operator = login("operator", "operator123");

        JsonNode created = submit(operator, body("it-approve", "4111111111111111", "150.00"), 201);
        assertThat(created.get("status").asText()).isEqualTo("APPROVED");
        assertThat(created.get("responseCode").asText()).isEqualTo("00");
        assertThat(created.get("maskedCardNumber").asText()).isEqualTo("****1111");
        assertThat(created.toString()).doesNotContain("4111111111111111");
        UUID id = UUID.fromString(created.get("id").asText());

        mvc.perform(get("/api/transactions/" + id).header("Authorization", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.issuerId").value("ISSUER-VISA-SIM"));

        // Events are published after commit and consumed asynchronously into the audit table.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(auditRecords.findByTransactionIdOrderByOccurredAtAscIdAsc(id))
                        .extracting(AuditRecord::getStatus)
                        .containsExactly(TransactionStatus.RECEIVED, TransactionStatus.VALIDATED,
                                TransactionStatus.ROUTED, TransactionStatus.APPROVED));

        List<AuditRecord> trail = auditRecords.findByTransactionIdOrderByOccurredAtAscIdAsc(id);
        assertThat(trail).extracting(AuditRecord::getKafkaPartition).containsOnly(trail.get(0).getKafkaPartition());

        mvc.perform(get("/api/transactions/" + id + "/events").header("Authorization", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].status").value("APPROVED"));
    }

    @Test
    void resubmittingTheSameIdempotencyKeyIs409AndProcessesNothing() throws Exception {
        String operator = login("operator", "operator123");

        submit(operator, body("it-dup", "4111111111111111", "20.00"), 201);
        JsonNode conflict = submit(operator, body("it-dup", "4111111111111111", "20.00"), 409);
        assertThat(conflict.get("status").asInt()).isEqualTo(409);
    }

    @Test
    void overLimitAmountIsDeclinedNotRejected() throws Exception {
        String operator = login("operator", "operator123");

        JsonNode declined = submit(operator, body("it-limit", "5555555555554444", "10000.01"), 201);
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declined.get("responseCode").asText()).isEqualTo("61");
    }

    @Test
    void rolesAreEnforcedEndToEnd() throws Exception {
        String operator = login("operator", "operator123");
        String admin = login("admin", "admin123");

        mvc.perform(get("/api/transactions").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/transactions").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
