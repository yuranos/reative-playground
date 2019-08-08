package com.yuranos.reactive

import org.reactivestreams.Publisher
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.publisher.TopicProcessor
import reactor.core.publisher.UnicastProcessor
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import reactor.test.publisher.PublisherProbe
import reactor.test.publisher.TestPublisher
import reactor.util.context.Context
import java.io.IOException
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


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

        Flux
            .error<String>(IllegalArgumentException())
            .doOnError { println(it) }
            .retryWhen { companion -> companion.take(3) }
            .subscribe()




        Flux.error<String>(IllegalArgumentException())
            .retryWhen { companion ->
                companion
                    .zipWith(Flux.range(1, 4),
                        { error, index ->
                            if (index < 4)
                                index
                            else
                                throw Exceptions.propagate(error)
                        })
            }.subscribe()

        //Generator
//    Generally, these operators are made to bridge APIs that are not reactive,
//    providing a "sink" that is similar in concept to a Processor in the sense that
//    it lets you manually populate the sequence with data or terminate it).?
        val flux = Flux.generate(
            { 0 },
            { state, sink ->
                sink.next("3 x " + state + " = " + 3 * state)
                if (state == 10) sink.complete()
                state + 1
            }).subscribe {
            println("Good for sequential, blocking subscription: $it")
        }


//    Exception Handling
        val converted = Flux
            .range(1, 10)
            .map {
                try {
                    convert(it)
                } catch (e: IOException) {
                    throw Exceptions.propagate(e)
                }
            }

        converted.subscribe(
            { v -> println("RECEIVED: $v") },
            { e ->
                if (Exceptions.unwrap(e) is IOException) {
                    println("Something bad happened with I/O")
                } else {
                    println("Something bad happened")
                }
            }
        )

        //Processors
        //UnicastProcessor - one subscriber, has internal buffer.
        val processor = UnicastProcessor.create<Int>()
        processor.subscribe {
            println("First$it")
        }
        //Failing with: IllegalStateException: UnicastProcessor allows only a single Subscriber
//    processor.subscribe {
//        println("Second$it")
//    }
        //Two sinks can emit events to the same Processor
        val sink = processor.sink()
        sink.next(1)
        sink.next(1)

        val sink2 = processor.sink()
        sink2.next(2)
        sink2.next(2)

        sink.next(1)
        sink.next(1)


        //TopicProcessor - async
        val topicProcessor = TopicProcessor.create<Int>()
        topicProcessor.subscribe {
            println("First$it")
        }
//    Works fine
        topicProcessor.subscribe {
            println("Second$it")
        }


        //Two sinks can emit events to the same Processor
        val sink3 = topicProcessor.sink()
        sink3.next(1)
        sink3.next(1)

        val sink4 = topicProcessor.sink()
        sink4.next(2)
        sink4.next(2)

        sink3.next(1)
        sink3.next(1)


        //Testing
        val sut = Flux.just(
            "Ok", "Good", "Worse", java.lang.IllegalArgumentException()
        )

//    StepVerifier
//        .create(sut)
//        .expectNext("Ok")
//        .expectNext("Good")
//        .expectNext("Worse")
//        .verifyError()


        StepVerifier
            .withVirtualTime {
                Mono.delay(Duration.ofDays(1))
            }
            .expectNext(0L)
            .verifyComplete()
//        .expectErrorMessage("boom")
//        .verify()


//    val flux = Flux.just(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)
//    StepVerifier.create(flux)
//        .expectNext(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)
//        .expectComplete()
//        .verify()


//    val publisher = TestPublisher.createNoncompliant(TestPublisher.Violation.ALLOW_NULL,
//        TestPublisher.Violation.REQUEST_OVERFLOW).next(1)

        val publisher = TestPublisher.create().next(1)
        val faultyFlux = publisher.flux()
        faultyFlux.subscribe {
            println(it)
        }
        publisher.next(2).next(3).assertCancelled()

        //Debugging
        //Globally changing stacktrace
        Hooks.onOperatorDebug()


        //Advanced topics

        //Transform vs compose
//    Compose - lazy and for each subscriber
        //Transform()
        val filterAndMap1: (Flux<String>) -> Flux<String> = {
            it.filter { color -> color != "orange" }
                .map(String::toUpperCase)
        }

        Flux.fromIterable(Arrays.asList("blue", "green", "orange", "purple"))
            //prints orange as well
            .doOnNext { println(it) }
            .transform(filterAndMap1)
        //no orange here
//        .subscribe { d -> println("Subscriber to Transformed MapAndFilter: $d") }


//    Compose()
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

        composedFlux.subscribe { d -> println("Subscriber 1 to Composed MapAndFilter :$d") }
        composedFlux.subscribe { d -> println("Subscriber 2 to Composed MapAndFilter: $d") }


        //Error handling
        //TODO: Not clear for now
        val atomicCounter = AtomicInteger(4)
        Flux.range(1, 10)
            .compose {
                if (atomicCounter.incrementAndGet() == 5)
                    return@compose Flux.error(RuntimeException())
                else
                    it
            }
            .onErrorResume {
                println("Caught")
                Flux.error(it)
            }.subscribe { println("Consumed value: $it") }

        //ConnectableFlux
        val source = Flux.range(1, 3)
            .doOnSubscribe { println("subscribed to source") }

        val co = source.publish()

        co.subscribe { println(it) }
        co.subscribe { println(it) }

        println("done subscribing")
        Thread.sleep(500)
        println("will now connect")

        //Data emission will only start here. Without ConnectableFlux emission will start at .subscribe()
        // and will likely be done with by the time you reach this code.
//    co.connect();
        val refCount = co.refCount(2)
        refCount
            //this will stop both subscribers
//        .limitRequest(0)
            .subscribe()
        refCount.subscribe()

        //Same thing but with autoconnect(n)
        val autoCo = Flux.range(1, 3)
            .doOnSubscribe { println("subscribed to source") }
            .publish()
            .autoConnect(2)

        autoCo.subscribe { println(it) }
        //Unless uncommented nothing happens
//    autoCo.subscribe { println(it) }

        println("done subscribing")
        Thread.sleep(500)
        println("will now connect")

        //Batching
        StepVerifier.create(
            Flux.just(1, 3, 5, 2, 4, 6, 11, 12, 13)
                .groupBy { i -> if (i % 2 == 0) "even" else "odd" }
                .concatMap { g ->
                    g.defaultIfEmpty(-1) //if empty groups, show them
                        .map { it.toString() } //map to string
                        .startWith(g.key()) //start with the group's key
                }
        )
            .expectNext("odd", "1", "3", "5", "11", "13")
            .expectNext("even", "2", "4", "6", "12")
            .verifyComplete()

        //Parallelization
        Flux.range(1, 10)
            .parallel(2)
            //This method is not available
//        .subscribeOn()
            .runOn(Schedulers.parallel())
            //IMPORTANT: Note also that subscribe(Subscriber<T>) merges all the rails, while subscribe(Consumer<T>) runs all the rails.
            .subscribe({ i -> println(Thread.currentThread().name + " -> " + i) })

        //Contexts
        val key = "message"
        val r = Mono.just("Hello")
            .flatMap { s ->
                Mono.subscriberContext()
                    .map { ctx -> s + " " + ctx.get(key) }
            }
            .subscriberContext { ctx -> ctx.put(key, "World") }

        StepVerifier.create(r)
            .expectNext("Hello World")
            .verifyComplete()


        Mono.just("One")
            .subscriberContext(Context.of("One", "One"))
            .map { "Two" }
            .subscriberContext(Context.of("Two", "Two"))
            .flatMap {
                Mono.just("Three")
            }
            .subscriberContext(Context.of("Three", "Three"))
            .subscribe {
                Mono.subscriberContext().subscribe {
                    //will throw anexception that the context is empty because it is populated form bottom to top.
                    println(it[0] as String)
                }
            }
    }
    //Context immutability
    val key = "message"

    val r = Mono.subscriberContext()
//        .subscriberContext(Context.of(key, "Hello"))
        .map { ctx -> ctx.put(key, "Hello") }
        .flatMap { Mono.subscriberContext() }
        .map { ctx -> ctx.getOrDefault(key, "Default") }

    StepVerifier.create(r)
        .expectNext("Default")
        .verifyComplete()


}


fun testEmptyPathIsUsed() {
    StepVerifier.create(processOrFallback(Mono.empty(), Mono.just("EMPTY_PHRASE")))
        .expectNext("EMPTY_PHRASE")
        .verifyComplete()
}

fun testCommandEmptyPathIsUsed() {
    //probe will allow us to capture the state of the reactive execution
    val probe = PublisherProbe.empty<Void>()

    StepVerifier.create(processOrFallback(Mono.empty(), probe.mono()))
        .verifyComplete()

    probe.assertWasSubscribed()
    probe.assertWasRequested()
    probe.assertWasNotCancelled()
}


fun processOrFallback(commandSource: Mono<String>, doWhenEmpty: Mono<Void>): Mono<Void> {
    return commandSource
        .flatMap { command -> executeCommand(command).then() }
        //then() will return Mono<Void> which is no different from an empty result.
        // We need to be able to distinguish between empty command and Mono<Void>
        .switchIfEmpty(doWhenEmpty)
}

fun processOrFallback(source: Mono<String>, fallback: Publisher<String>): Flux<String> {
    return source
        .flatMapMany { phrase -> Flux.fromArray(phrase.split("\\s+").toTypedArray()) }
        .switchIfEmpty(fallback)
}

private fun executeCommand(command: String): Mono<String> {
    return Mono.just("$command DONE")
}

//class SampleSubscriber<T> : BaseSubscriber<T>() {
//
//    override fun hookOnSubscribe(subscription: Subscription) {
//        println("Still subscribed")
//        request(1)
//    }
//
//    public override fun hookOnNext(value: T?) {
//        println("Still onNexting")
//        println(value)
//        request(1)
//    }
//}


@Throws(IOException::class)
fun convert(i: Int): String {
    if (i > 3) {
        throw IOException("boom $i")
    }
    return "OK $i"
}
