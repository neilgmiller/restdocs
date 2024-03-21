package com.allego.api.client.support.futureproof

interface EnumID<T> {
    val id: T
}

data class ImmutableEnumID<T>(override val id: T) : EnumID<T>