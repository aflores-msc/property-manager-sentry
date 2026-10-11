package com.propertymanager.sentry.triage;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.Map;

import com.propertymanager.sentry.mail.InboundEmail;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The triage workflow as a LangGraph4j state graph:
 *
 * <pre>
 *   START ─▶ supervisor ─┬─▶ maintenance ─▶ END
 *                        ├─▶ cityNotice  ─▶ END
 *                        ├─▶ dispute     ─▶ END
 *                        └─▶ (IGNORED)   ─▶ END
 * </pre>
 *
 * The graph is compiled once at startup and is safe to invoke from several threads at a time.
 */
@Component
public class TriageGraph {

    static final String SUPERVISOR = "supervisor";
    static final String MAINTENANCE = "maintenance";
    static final String CITY_NOTICE = "cityNotice";
    static final String DISPUTE = "dispute";

    private static final Logger log = LoggerFactory.getLogger(TriageGraph.class);

    private final CompiledGraph<TriageState> graph;

    public TriageGraph(TriageAgents agents) throws GraphStateException {
        this.graph = new StateGraph<>(TriageState::new)
                .addNode(SUPERVISOR, node_async(state -> Map.of(
                        TriageState.ROUTING, agents.route(state.email()))))
                .addNode(MAINTENANCE, node_async(state -> Map.of(
                        TriageState.DETAILS, agents.extractMaintenance(state.email(), routingOf(state)))))
                .addNode(CITY_NOTICE, node_async(state -> Map.of(
                        TriageState.DETAILS, agents.extractCityNotice(state.email(), routingOf(state)))))
                .addNode(DISPUTE, node_async(state -> Map.of(
                        TriageState.DETAILS, agents.extractDispute(state.email(), routingOf(state)))))
                .addEdge(START, SUPERVISOR)
                .addConditionalEdges(SUPERVISOR, edge_async(TriageGraph::nextStep), Map.of(
                        MAINTENANCE, MAINTENANCE,
                        CITY_NOTICE, CITY_NOTICE,
                        DISPUTE, DISPUTE,
                        END, END))
                .addEdge(MAINTENANCE, END)
                .addEdge(CITY_NOTICE, END)
                .addEdge(DISPUTE, END)
                .compile();
    }

    /** Runs one email through the graph. Never throws: failures come back as {@link TriageOutcome.Failed}. */
    public TriageOutcome triage(InboundEmail email) {
        try {
            TriageState result = graph.invoke(GraphInput.args(Map.of(TriageState.EMAIL, email)), RunnableConfig.empty())
                    .orElseThrow(() -> new IllegalStateException("graph produced no final state"));
            return toOutcome(result);
        } catch (RuntimeException e) {
            Throwable cause = rootCause(e);
            log.warn("Triage failed for message {} ({}): {}", email.id(), email.subject(), cause.toString());
            return new TriageOutcome.Failed(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    /** Conditional edge: which specialist (if any) handles the email. */
    static String nextStep(TriageState state) {
        return switch (routingOf(state).category()) {
            case MAINTENANCE -> MAINTENANCE;
            case CITY_NOTICE -> CITY_NOTICE;
            case TENANT_DISPUTE -> DISPUTE;
            case IGNORED -> END;
        };
    }

    private static TriageOutcome toOutcome(TriageState state) {
        RoutingDecision routing = routingOf(state);
        if (routing.category() == Category.IGNORED) {
            return new TriageOutcome.Ignored(routing);
        }
        return state.details()
                .<TriageOutcome>map(details -> new TriageOutcome.Ticketed(routing, details))
                .orElseGet(() -> new TriageOutcome.Failed("specialist produced no details"));
    }

    private static RoutingDecision routingOf(TriageState state) {
        RoutingDecision routing = state.routing()
                .orElseThrow(() -> new IllegalStateException("supervisor produced no routing decision"));
        if (routing.category() == null || routing.priority() == null) {
            throw new IllegalStateException("supervisor returned an incomplete routing decision: " + routing);
        }
        return routing;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
