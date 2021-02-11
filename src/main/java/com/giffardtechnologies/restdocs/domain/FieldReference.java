package com.giffardtechnologies.restdocs.domain;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FieldReference {
	final String mNode;
	@Nullable
	final FieldReference mChild;

	public static FieldReference fromParts(List<String> parts) {
		String node = parts.remove(0);
		if (parts.isEmpty()) {
			return new FieldReference(node, null);
		} else {
			return new FieldReference(node, fromParts(parts));
		}
	}
	public static FieldReference fromString(String reference) {
		List<String> parts = Arrays.stream(reference.split("\\.")).collect(Collectors.toList());
		return fromParts(parts);
	}

	public FieldReference(String node, @Nullable FieldReference child) {
		mChild = child;
		mNode = node;
	}

	public String getNode() {
		return mNode;
	}

	@Nullable
	public FieldReference getChild() {
		return mChild;
	}

	/**
	 * Tests if the current node is equal to the passed string.
	 *
	 * @param node node string to check against
	 *
	 * @return true if the current node equals passed node, false otherwise
	 */
	public boolean isNode(String node) {
		return mNode.equals(node);
	}

	/**
	 * Tests if the current node is equal to the passed string and that this is a leaf node.
	 *
	 * @param node node string to check against
	 *
	 * @return true if the current node equals passed node and is a leaf, false otherwise
	 */
	public boolean isLeafNode(String node) {
		return mNode.equals(node) && mChild == null;
	}

	/**
	 * Tests if the current node is a leaf node.
	 *
	 * @return true if the current node is a leaf, false otherwise
	 */
	public boolean isLeafNode() {
		return mChild == null;
	}

}
