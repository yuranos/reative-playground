package com.yuranos.reactive

import reactor.core.publisher.Flux

fun main() {
    //always stops at error
    val failFastFlux = Flux.range(1, 5)
        .map { i ->
            if (i == 1 || i == 2 || i == 4 || i == 5) return@map i
            else throw RuntimeException()
        }
    failFastFlux.subscribe(
        ::println
    ) { println("Error: $it") }

    val fluxWithErrorHandler = Flux.range(1, 5)
        .map { i ->
            if (i == 1 || i == 2 || i == 4 || i == 5) return@map i
            else throw RuntimeException()
        }.onErrorContinue { err, _ -> println(err) }
    fluxWithErrorHandler.subscribe(
        ::println
    ) { println("I won't be called since the errors are recovered") }
}
