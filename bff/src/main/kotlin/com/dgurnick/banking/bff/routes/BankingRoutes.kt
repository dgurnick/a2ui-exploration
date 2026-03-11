package com.dgurnick.banking.bff.routes

import com.expediagroup.graphql.server.ktor.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRoutes() {
    routing {
        graphQLPostRoute()           // POST /graphql  – queries & mutations
        graphQLSDLRoute()            // GET  /sdl      – schema SDL download
        graphiQLRoute()             // GET  /graphiql – browser playground
        graphQLSubscriptionsRoute() // WS   /subscriptions – streaming
    }
}
