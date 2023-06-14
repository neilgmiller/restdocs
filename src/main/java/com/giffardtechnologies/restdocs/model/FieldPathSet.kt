package com.giffardtechnologies.restdocs.model

import io.vavr.collection.HashMap
import io.vavr.collection.Map

class FieldPathSet private constructor(private val childPathElements: Map<String, FieldPathNode>) : Iterable<FieldPathNode> {

    companion object  {

        fun ofAll(branches: Collection<FieldPath>): FieldPathSet {
            val pathSet = mutableMapOf<String, FieldPathBuilderNode>()

            val sortedBranches = branches.sortedWith(Comparator.naturalOrder())
            sortedBranches.forEach { pathSet.addExcludePath(it) }

            return FieldPathSet(HashMap.ofAll(pathSet).mapValues { it.build() } )
        }

        private data class PreviousNodeData(val branch: FieldPath, val node: MutableList<FieldPathBuilderNode.Leaf>)

//        private fun MutableMap<String, FieldPathNode>.addTrimPoint(path: FieldPath) {
//            val existingNode = this[fieldPathNode.field]
//            if (existingNode == null) {
//
//            }
//        }

        private fun MutableMap<String, FieldPathBuilderNode>.addExcludePath(fieldPath: FieldPath) {
            var mutableMap = this
            val lastIndex = fieldPath.pathElements.size() - 1
            fieldPath.pathElements.forEachWithIndex { element, i ->
                val existingNode = mutableMap[element]
                if (existingNode == null) {
                    val node = if (i == lastIndex) {
                        FieldPathBuilderNode.Leaf(element)
                    } else {
                        FieldPathBuilderNode.Stem(element, mutableMapOf())
                    }
                    mutableMap[element] = node
                    if (node is FieldPathBuilderNode.Stem) {
                        mutableMap = node.childPathElements
                    }
                } else {
                    when(existingNode) {
                        is FieldPathBuilderNode.Leaf -> throw IllegalArgumentException("Parent path is already excluded: $fieldPath at $element")
                        is FieldPathBuilderNode.Stem -> {
                            if (i == lastIndex) {
                                throw IllegalArgumentException("Child path is already excluded: $fieldPath at $element")
                            } else {
                                mutableMap = existingNode.childPathElements
                            }
                        }
                    }
                }
            }
        }

        sealed class FieldPathBuilderNode(val field: String) {
            abstract fun build() : FieldPathNode

            class Leaf(field: String) : FieldPathBuilderNode(field) {
                override fun build() = FieldPathLeaf(field)
            }
            class Stem(field: String, val childPathElements: MutableMap<String, FieldPathBuilderNode>) : FieldPathBuilderNode(field) {
                override fun build() = if (childPathElements.isEmpty()) {
                    throw IllegalStateException("Attempt to build stem with no children")
                } else {
                    FieldPathStem(field, FieldPathSet(HashMap.ofAll(childPathElements.mapValues { it.value.build() })))
                }
            }
        }

    }

    override fun iterator(): Iterator<FieldPathNode> {
        return childPathElements.values().iterator()
    }

    operator fun get(fieldName: String): FieldPathNode? {
        return childPathElements[fieldName].orNull
    }


}

sealed class FieldPathNode(val field: String)

class FieldPathLeaf(field: String) : FieldPathNode(field)

class FieldPathStem(field: String, val childPathElements: FieldPathSet) : FieldPathNode(field)

