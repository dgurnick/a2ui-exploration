package com.dgurnick.bff

import com.expediagroup.graphql.server.ktor.GraphQL
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import com.dgurnick.bff.agent.AccountBalanceAgent
import com.dgurnick.bff.agent.AtmFinderAgent
import com.dgurnick.bff.agent.BankOffersAgent
import com.dgurnick.bff.agent.FallbackAgent
import com.dgurnick.bff.graphql.A2uiMutation
import com.dgurnick.bff.graphql.A2uiQuery
import com.dgurnick.bff.graphql.A2uiSubscription
import com.dgurnick.bff.routes.configureRoutes
import com.dgurnick.bff.usecase.UseCase
import java.time.Duration

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // WebSockets are required for GraphQL subscriptions
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
    }

    // graphql-kotlin Ktor plugin – auto-generates schema from annotated classes
    val useCases: List<UseCase> = listOf(
        AtmFinderAgent(),
        AccountBalanceAgent(),
        BankOffersAgent(),
        FallbackAgent(),   // catch-all — must be last
    )
    install(GraphQL) {
        schema {
            packages = listOf("com.dgurnick.bff")
            queries = listOf(A2uiQuery())
            mutations = listOf(A2uiMutation())
            subscriptions = listOf(A2uiSubscription(useCases))
        }
    }

    configureRoutes()
}
