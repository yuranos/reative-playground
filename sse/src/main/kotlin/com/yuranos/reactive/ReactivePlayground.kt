package com.yuranos.reactive

import org.reactivestreams.Subscription
import reactor.core.Exceptions
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.function.BiFunction


fun main() {
    //always stops at error
    if (false) {
        val failFastFlux = Flux.range(1, 5)
            .map { i ->
                if (i == 1 || i == 2 || i == 4 || i == 5) return@map i
                else throw RuntimeException()
            }
        failFastFlux.subscribe(
            ::println
        ) { println("Error: $it") }

        //Will log error and continue
        val fluxWithErrorHandler = Flux.range(1, 5)
            .map { i ->
                Thread.sleep(1000)
                if (i == 1 || i == 2 || i == 4 || i == 5) return@map i
                else throw RuntimeException()
            }.onErrorContinue { err, _ -> println(err) }
        fluxWithErrorHandler.subscribe(
            ::println,
            { println("I won't be called since the errors are recovered") },
            { println("Completion event") })


        //with managing Subscription manually
        val fluxWithSubscription1 = Flux.range(1, 5)
            .map { i ->
                Thread.sleep(1000)
                if (i == 1 || i == 2 || i == 4 || i == 5) return@map i
                else throw RuntimeException()
            }.onErrorContinue { err, _ -> println(err) }
        fluxWithSubscription1.subscribe(
            ::println,
            { println("I won't be called since the errors are recovered") },
            { println("Completion event") },
            { sub -> sub.request(10) })


        //Disposable with async Flux. Emitter continues to emit even when Subscription is disposed of.
        Flux.create<Int> { emitter ->
            Thread {
                while (true) {
                    Thread.sleep(1000)
                    println("Still emitting")
                    emitter.next(1)
                }
            }.start()

        }.subscribe { println("Still receiving. Got a new value: $it") }
            //But subscriber is not interested in the events any longer.
            //Only makes sense to use in async scenario. Otherwise, dispose() will not even be called
            // because subscribe doesn't return untill the events are emitted.
            .dispose()
        println("I'm here")


        //Concurrency
        val fluxWithSubscription = Flux.range(0, 1000)
            .limitRate(100)
            .parallel(5)

            .log()
        fluxWithSubscription.subscribe(
            ::println,
            { println("I won't be called since the errors are recovered") },
            { println("Completion event") },
            { sub -> sub.request(20) })
        println("I'm here")


        //publishOn
        val s = Schedulers.newParallel("parallel-scheduler", 4)
        val fluxPublishOn = Flux
            .range(1, 2)
            .map {
                //executes on one thread. Can mean that several maps will run before any after-publishOn operations processed
                Thread.sleep(300)
                println("In map 1. ${Thread.currentThread().name}")
                10 + it
            }
            .publishOn(s)
            .map {
                //execute on a different thread
                println("In map 2. ${Thread.currentThread().name}")
                "value $it"
            }

        Thread { fluxPublishOn.subscribe(::println, ::println, { println("PublishOn Done") }) }.start()

        println("SubscribeOn logic-------------")

        //All in one, but not main, thread
        val fluxSubscribeOn = Flux
            .range(1, 2)
            .map {
                println("In map 1. ${Thread.currentThread().name}")
                10 + it
            }
            .subscribeOn(s)
            .map {
                println("In map 2. ${Thread.currentThread().name}")
                "value $it"
//    fluxSubscribeOn.subscribe(::println, ::println, { println("Done") })
                Flux.interval(Duration.ofMillis(250))
                    .map { input ->
                        if (input < 3) return@map "tick $input"
                        throw RuntimeException("boom")
                    }
                    //allows to proceed despite exception. Keep in mind: it's a completely new subscription
                    .retry(1)
                    .elapsed()
                    .subscribe(System.out::println, System.err::println)


                Thread.sleep(2100)
            }
    }

    Flux
        .error<String>(IllegalArgumentException())
        .doOnError { println(it) }
        .retryWhen { companion -> companion.take(3) }
        .subscribe()


    Flux.error<String>(IllegalArgumentException())
        .retryWhen { companion ->
            companion
                .zipWith(Flux.range(1, 4),
                    BiFunction<Throwable, Int, Int> { error, index ->
                        if (index < 4)
                            return@BiFunction index
                        else
                            throw Exceptions.propagate(error)
                    })
        }.subscribe()

}

class SampleSubscriber<T> : BaseSubscriber<T>() {

    override fun hookOnSubscribe(subscription: Subscription) {
        println("Still subscribed")
        request(1)
    }

    public override fun hookOnNext(value: T?) {
        println("Still onNexting")
        println(value)
        request(1)
    }
}
