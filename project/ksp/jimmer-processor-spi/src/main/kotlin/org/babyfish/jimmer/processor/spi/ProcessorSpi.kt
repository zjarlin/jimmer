package org.babyfish.jimmer.processor.spi

interface ProcessorSpi<T, R> {
    var ctx: T
    fun process(): R
    val phase: Int get() = 1
    val order: Int get() = 0
}
