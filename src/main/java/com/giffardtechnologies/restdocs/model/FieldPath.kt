package com.giffardtechnologies.restdocs.model

import io.vavr.collection.Array

//
//edWith { o1, o2 ->
//    for (i in 0 until min(o1.size, o2.size)) {
//        val compareResult = o1[i].compareTo(o2[i])
//        if (compareResult != 0) {
//            return@sortedWith compareResult
//        }
//    }
//    -1
//}

class FieldPath private constructor(val path: String, val pathElements: Array<String>) : Comparable<FieldPath> {

    constructor(path: String): this(path, Array.ofAll(path.split('.')))

    constructor(path: Iterable<String>): this(path.joinToString( "."), Array.ofAll(path))

    val name: String = run { pathElements.last() }
    val parent = run {
        if (pathElements.size() == 1) {
            null
        } else {
            FieldPath(pathElements.removeAt(pathElements.size() - 1))
        }
    }

    override fun compareTo(other: FieldPath): Int {
        return path.compareTo(other.path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FieldPath

        return path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

    override fun toString(): String {
        return "FieldPath(path='$path')"
    }

}
