package com.propertymanager.sentry.triage;

import java.util.Map;
import java.util.Optional;

import com.propertymanager.sentry.mail.InboundEmail;
import org.bsc.langgraph4j.state.AgentState;

/**
 * The state object that flows through the LangGraph4j graph. Each node returns a partial map of
 * updates, which LangGraph4j merges into the state (the last write wins for every key here).
 */
public class TriageState extends AgentState {

    static final String EMAIL = "email";
    static final String ROUTING = "routing";
    static final String DETAILS = "details";

    public TriageState(Map<String, Object> data) {
        super(data);
    }

    public InboundEmail email() {
        return this.<InboundEmail>value(EMAIL).orElseThrow();
    }

    public Optional<RoutingDecision> routing() {
        return value(ROUTING);
    }

    public Optional<TicketDetails> details() {
        return value(DETAILS);
    }
}
