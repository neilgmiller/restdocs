package com.giffardtechnologies.restdocs.vavr

import io.vavr.collection.Array

fun <E: Any, T: Any> Array<E>.mapNonNull(mapper: (E) -> T?): Array<T> {
    return this.mapNotNull(mapper).toImmutable()
}

fun <E> List<E>.toImmutable(): Array<E> {
    return Array.ofAll(this)
}

fun <T> Array<T>.isNotEmpty(): Boolean {
    return !this.isEmpty
}
