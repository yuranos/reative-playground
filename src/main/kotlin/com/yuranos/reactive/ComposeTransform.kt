package com.yuranos.reactive

import reactor.core.publisher.Flux
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val ai = AtomicInteger()
    val filterAndMap2: (Flux<String>) -> Flux<String> = label@{
        if (ai.incrementAndGet() == 1) {
            return@label it.filter { color -> color != "orange" }
                .map(String::toUpperCase)
        }
        //This filtering will only be done for compose(),
        //transform() will only ever be called once for all subscribers, so it will end up in a previous expression.
        return@label it.filter { color -> color != "purple" }
            .map(String::toUpperCase)
    }

    val composedFlux = Flux.fromIterable(Arrays.asList("blue", "green", "orange", "purple"))
        .doOnNext { println(it) }
        //Compose is called for every single subscriber. So if the function has state it will impact the produced values.
        //It will produce BLUE-GREEN-PURPLE and then BLUE-GREEN-ORANGE
        .compose(filterAndMap2)
//        .transform(filterAndMap2)

//    composedFlux.subscribe { d -> println("Subscriber 1 to Composed MapAndFilter :$d") }
//    composedFlux.subscribe { d -> println("Subscriber 2 to Composed MapAndFilter: $d") }

    val transformFlux = Flux.fromIterable(Arrays.asList("blue", "green", "orange", "purple"))
        .doOnNext { println(it) }
        //Compose is called for every single subscriber. So if the function has state it will impact the produced values.
        //It will produce BLUE-GREEN-PURPLE and then BLUE-GREEN-ORANGE
//        .compose(filterAndMap2)
        .transform(filterAndMap2)

    transformFlux.subscribe { d -> println("Subscriber 1 to Transform MapAndFilter :$d") }
    transformFlux.subscribe { d -> println("Subscriber 2 to Transform MapAndFilter: $d") }
}
