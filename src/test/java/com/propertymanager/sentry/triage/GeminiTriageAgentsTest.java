package com.propertymanager.sentry.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.propertymanager.sentry.config.SentryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

/**
 * Checks the Spring AI side without calling Gemini: prompt templates render with the email
 * inside the guarded block, and the JSON answer is mapped onto the record.
 */
class GeminiTriageAgentsTest {

    @Test
    void rendersPromptsAndMapsTheStructuredAnswer() {
        var sentPrompt = new AtomicReference<Prompt>();
        ChatModel model = prompt -> {
            sentPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                    {"category":"MAINTENANCE","priority":"URGENT","senderType":"tenant",
                     "propertyAddress":"12 Oak St","unitNumber":"4B","rationale":"active leak"}"""))));
        };

        RoutingDecision decision = agents(model).route(Fixtures.email("1", "Water everywhere {urgent}"));

        assertThat(decision).isEqualTo(new RoutingDecision(Category.MAINTENANCE, Priority.URGENT, "tenant",
                "12 Oak St", "4B", "active leak"));
        String system = sentPrompt.get().getSystemMessage().getText();
        String user = sentPrompt.get().getUserMessage().getText();
        assertThat(system).contains("untrusted data");
        assertThat(user).contains("Categories:", "<email>", "Subject: Water everywhere {urgent}", "</email>");
    }

    @Test
    void specialistPromptMentionsThePropertyFoundBySupervisor() {
        var sentPrompt = new AtomicReference<Prompt>();
        ChatModel model = prompt -> {
            sentPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                    {"summary":"Noise","disputeType":"noise","partiesInvolved":"4B, 5B","complaint":"music",
                     "desiredResolution":"quiet","escalationNeeded":false}"""))));
        };

        DisputeDetails details = agents(model).extractDispute(Fixtures.email("1", "Noise"),
                Fixtures.routing(Category.TENANT_DISPUTE, Priority.MEDIUM));

        assertThat(details.disputeType()).isEqualTo("noise");
        assertThat(sentPrompt.get().getUserMessage().getText()).contains("12 Oak St, unit 4B");
    }

    private static GeminiTriageAgents agents(ChatModel model) {
        var properties = new SentryProperties(null, null, new SentryProperties.Triage(1, 10_000, java.time.Duration.ofSeconds(30)));
        return new GeminiTriageAgents(ChatClient.builder(model), properties,
                new ClassPathResource("prompts/system.st"),
                new ClassPathResource("prompts/supervisor.st"),
                new ClassPathResource("prompts/maintenance.st"),
                new ClassPathResource("prompts/city-notice.st"),
                new ClassPathResource("prompts/dispute.st"));
    }
}
