package com.yuranos.reactive

import reactor.core.publisher.Flux

fun main() {
    Flux.range(0, 5).concatWithValues(6, 7, 8).subscribe { println(it) }


    val evenNumbers = Flux
        .range(0, 10)
        .filter({ x -> x % 2 == 0 }) // i.e. 2, 4

    val oddNumbers = Flux
        .range(0, 10)
        .filter({ x -> x % 2 > 0 })  // ie. 1, 3, 5

//    val fluxOfIntegers = Flux.concat(
//        evenNumbers,
//        oddNumbers
//    )


}
