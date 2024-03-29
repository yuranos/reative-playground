/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 *
 * @author hantsy
 */
@Component
class PostRepository {

    private static final List<Post> DATA = new ArrayList<>();

    static {
        DATA.add(new Post(1L, "post one", "content of post one"));
        DATA.add(new Post(2L, "post two", "content of post two"));
    }

    Flux<Post> findAll() {
        return Flux.fromIterable(DATA);
    }

    Mono<Post> findById(Long id) {
        return findAll().filter(p -> Objects.equals(p.getId(), id)).single();
    }

    Mono<Post> save(Post post) {
//        long id = DATA.size() + 1;
//        Post saved = Post.builder().id(id).title(post.getTitle()).content(post.getContent()).build();
        DATA.add(post);
        return Mono.just(post);
    }

}
