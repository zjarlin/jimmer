package org.babyfish.jimmer.processor.spi

interface ProcessorSpi<T, R> {
    var ctx: T
    fun process(): R
}
