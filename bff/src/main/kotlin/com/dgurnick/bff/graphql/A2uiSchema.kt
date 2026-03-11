package com.dgurnick.bff.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.operations.Subscription
import kotlinx.coroutines.flow.Flow
import com.dgurnick.bff.agent.RestaurantFinderAgent
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("a2ui.graphql")

// ──────────────────────────────────────────────────────────────────────────────
// Output types
// ──────────────────────────────────────────────────────────────────────────────

@GraphQLDescription("A2UI agent capabilities (A2A agent card)")
data class AgentCard(
    val name: String,
    val description: String,
    val version: String,
    val supportedCatalogIds: List<String>,
    val acceptsInlineCatalogs: Boolean
)

@GraphQLDescription("Result of submitting a client event")
data class EventResult(
    val status: String,
    val received: String? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// Input types
// ──────────────────────────────────────────────────────────────────────────────

@GraphQLDescription("A user-initiated action from a rendered A2UI component")
data class UserActionInput(
    val name: String,
    val surfaceId: String,
    val sourceComponentId: String,
    val timestamp: String,
    /** Serialised JSON object of resolved context key-value pairs */
    val context: String = "{}"
)

@GraphQLDescription("A client-side rendering or binding error")
data class ClientErrorInput(
    val message: String,
    val componentId: String? = null,
    val details: String? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// Query
// ──────────────────────────────────────────────────────────────────────────────

class A2uiQuery : Query {
    @GraphQLDescription("Returns the agent capabilities (A2A agent card)")
    fun agentCard(): AgentCard = AgentCard(
        name = "A2UI Restaurant Finder",
        description = "Demo BFF agent that generates A2UI UIs for restaurant search",
        version = "0.8",
        supportedCatalogIds = listOf(
            "https://a2ui.org/specification/v0_8/standard_catalog_definition.json"
        ),
        acceptsInlineCatalogs = false
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Mutation
// ──────────────────────────────────────────────────────────────────────────────

class A2uiMutation : Mutation {
    @GraphQLDescription("Submit a user action event from a rendered A2UI surface")
    fun sendUserAction(input: UserActionInput): EventResult {
        log.info("userAction: name=${input.name} surface=${input.surfaceId} component=${input.sourceComponentId}")
        // In a production system this would route the event back to the active agent session.
        return EventResult(status = "ok", received = input.name)
    }

    @GraphQLDescription("Report a client-side rendering or binding error")
    fun reportError(input: ClientErrorInput): EventResult {
        log.warn("client error: ${input.message} (component=${input.componentId})")
        return EventResult(status = "logged")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Subscription
// ──────────────────────────────────────────────────────────────────────────────

class A2uiSubscription : Subscription {
    @GraphQLDescription(
        "Subscribe to an A2UI JSONL stream for the given prompt. " +
        "Each emission is one JSONL line conforming to the A2UI v0.8 server-to-client schema."
    )
    fun uiStream(
        @GraphQLDescription("Natural-language prompt sent to the agent")
        prompt: String,
        @GraphQLDescription("Target surface identifier (default: \"main\")")
        surfaceId: String = "main"
    ): Flow<String> {
        log.info("uiStream subscription: prompt='$prompt' surfaceId='$surfaceId'")
        return RestaurantFinderAgent(surfaceId).generate(prompt)
    }
}
