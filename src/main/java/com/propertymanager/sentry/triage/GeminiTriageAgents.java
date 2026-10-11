package com.propertymanager.sentry.triage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.propertymanager.sentry.config.SentryProperties;
import com.propertymanager.sentry.mail.InboundEmail;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link TriageAgents} backed by Gemini through Spring AI's {@link ChatClient}.
 *
 * <p>Every call asks for a Java record via {@code .entity(...)}. With
 * {@link AdvisorParams#ENABLE_NATIVE_STRUCTURED_OUTPUT}, Spring AI derives a JSON schema from the
 * record and hands it to Gemini as its response schema, so the model can only answer with JSON
 * of that exact shape. Spring AI then maps that JSON back onto the record.
 */
@Component
public class GeminiTriageAgents implements TriageAgents {

    private static final DateTimeFormatter RECEIVED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final ChatClient chatClient;
    private final int maxBodyChars;
    private final Resource supervisorPrompt;
    private final Resource maintenancePrompt;
    private final Resource cityNoticePrompt;
    private final Resource disputePrompt;

    public GeminiTriageAgents(
            ChatClient.Builder chatClientBuilder,
            SentryProperties properties,
            @Value("classpath:prompts/system.st") Resource systemPrompt,
            @Value("classpath:prompts/supervisor.st") Resource supervisorPrompt,
            @Value("classpath:prompts/maintenance.st") Resource maintenancePrompt,
            @Value("classpath:prompts/city-notice.st") Resource cityNoticePrompt,
            @Value("classpath:prompts/dispute.st") Resource disputePrompt) {
        // The shared system prompt (role + prompt-injection guard) is set once on the client.
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
        this.maxBodyChars = properties.triage().maxBodyChars();
        this.supervisorPrompt = supervisorPrompt;
        this.maintenancePrompt = maintenancePrompt;
        this.cityNoticePrompt = cityNoticePrompt;
        this.disputePrompt = disputePrompt;
    }

    @Override
    public RoutingDecision route(InboundEmail email) {
        return ask(supervisorPrompt, email, null, RoutingDecision.class);
    }

    @Override
    public MaintenanceDetails extractMaintenance(InboundEmail email, RoutingDecision routing) {
        return ask(maintenancePrompt, email, routing, MaintenanceDetails.class);
    }

    @Override
    public CityNoticeDetails extractCityNotice(InboundEmail email, RoutingDecision routing) {
        return ask(cityNoticePrompt, email, routing, CityNoticeDetails.class);
    }

    @Override
    public DisputeDetails extractDispute(InboundEmail email, RoutingDecision routing) {
        return ask(disputePrompt, email, routing, DisputeDetails.class);
    }

    private <T> T ask(Resource template, InboundEmail email, RoutingDecision routing, Class<T> type) {
        T answer = chatClient.prompt()
                .user(user -> user.text(template).params(templateParams(email, routing)))
                .call()
                .entity(type);
        if (answer == null) {
            throw new IllegalStateException("model returned no " + type.getSimpleName());
        }
        return answer;
    }

    Map<String, Object> templateParams(InboundEmail email, RoutingDecision routing) {
        return Map.of(
                "email", PromptSanitizer.wrapEmail(email, maxBodyChars, RECEIVED_FORMAT),
                "property", routing == null ? "" : PromptSanitizer.describeProperty(routing));
    }
}
