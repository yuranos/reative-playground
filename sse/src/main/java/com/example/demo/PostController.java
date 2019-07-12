/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.demo;

import java.time.Duration;
import java.util.Date;
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * @author hantsy
 */
@RestController
@RequestMapping(value = "/posts")
class PostController {

    private final PostRepository posts;

    public PostController(PostRepository posts) {
        this.posts = posts;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Flux<Post> all() {
//        Flux<Integer> ints = Flux.range(1, 4)
//                .map(i -> {
//                    if (i <= 3) return i;
//                    throw new RuntimeException("Got to 4");
//                });
//        ints.subscribe(System.out::println,
//                error
//                        -> System.err.println("Error: " + error));
//        return this.posts.findAll();
        return Flux.interval(Duration.ofSeconds(1L))
                .take(10).flatMap((oneSecond) -> this.posts.findAll());
    }

    @GetMapping(produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    public Flux<Post> stream() {
        return Flux.interval(Duration.ofSeconds(10L)).flatMap((oneSecond) -> this.posts.findAll());
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Post> sse() {
        return Flux
                .zip(Flux.interval(Duration.ofSeconds(10L)), this.posts.findAll().repeat())
                .map(Tuple2::getT2);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Mono<Post> create(@RequestBody Post post) {
        return this.posts.save(post);
    }

}
