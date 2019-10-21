package com.yuranos.reactive

import reactor.core.publisher.Mono
import java.lang.Thread.sleep


fun main() {
    Mono.just("Emitted value")
        .map {
            Thread {
                println("Something very bad will happen a bit later...")
                Thread.sleep(1000)
                throw Exception("Nasty exception")
            }.start()
        }
        .subscribe { println("Value received: $it") }

}
