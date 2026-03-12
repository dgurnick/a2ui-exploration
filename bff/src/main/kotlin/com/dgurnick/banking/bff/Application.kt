package com.dgurnick.banking.bff

import com.dgurnick.banking.bff.agent.AccountBalanceAgent
import com.dgurnick.banking.bff.agent.AtmFinderAgent
import com.dgurnick.banking.bff.agent.BankOffersAgent
import com.dgurnick.banking.bff.agent.FallbackAgent
import com.dgurnick.banking.bff.agent.LoanOffersAgent
import com.dgurnick.banking.bff.agent.SummaryAgent
import com.dgurnick.banking.bff.graphql.BankingMutation
import com.dgurnick.banking.bff.graphql.BankingQuery
import com.dgurnick.banking.bff.graphql.BankingSubscription
import com.dgurnick.banking.bff.routes.configureRoutes
import com.dgurnick.banking.bff.usecase.UseCase
import com.expediagroup.graphql.server.ktor.GraphQL
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
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
    val useCases: List<UseCase> =
            listOf(
                    AtmFinderAgent(),
                    AccountBalanceAgent(),
                    LoanOffersAgent(), // Must be before BankOffersAgent to handle loan-specific
                    // queries
                    BankOffersAgent(),
                    SummaryAgent(), // Handles goodbye/summary - before FallbackAgent
                    FallbackAgent(), // catch-all — must be last
            )
    install(GraphQL) {
        schema {
            packages = listOf("com.dgurnick.banking.bff")
            queries = listOf(BankingQuery())
            mutations = listOf(BankingMutation())
            subscriptions = listOf(BankingSubscription(useCases))
        }
    }

    configureRoutes()
}
