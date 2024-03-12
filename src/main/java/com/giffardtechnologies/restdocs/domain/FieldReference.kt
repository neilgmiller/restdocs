package com.giffardtechnologies.restdocs.domain

import java.util.*
import java.util.stream.Collectors

class FieldReference(val node: String, val child: FieldReference?) {

    /**
     * Tests if the current node is equal to the passed string.
     *
     * @param node node string to check against
     *
     * @return true if the current node equals passed node, false otherwise
     */
    fun isNode(node: String): Boolean {
        return this.node == node
    }

    /**
     * Tests if the current node is equal to the passed string and that this is a leaf node.
     *
     * @param node node string to check against
     *
     * @return true if the current node equals passed node and is a leaf, false otherwise
     */
    fun isLeafNode(node: String): Boolean {
        return this.node == node && child == null
    }

    val isLeafNode: Boolean
        /**
         * Tests if the current node is a leaf node.
         *
         * @return true if the current node is a leaf, false otherwise
         */
        get() = child == null

    companion object {
        fun fromParts(parts: MutableList<String>): FieldReference {
            val node: String = parts.removeAt(0)
            return if (parts.isEmpty()) {
                FieldReference(node, null)
            } else {
                FieldReference(
                    node,
                    fromParts(parts)
                )
            }
        }

        fun fromString(reference: String): FieldReference {
            val parts = Arrays.stream(reference.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                .collect(Collectors.toList())
            return fromParts(parts)
        }
    }
}
