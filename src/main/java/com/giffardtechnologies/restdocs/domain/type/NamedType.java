package com.giffardtechnologies.restdocs.domain.type;

/**
 * An interface for types that have an identifying name.
 */
public interface NamedType {

	/**
	 * Returns the human-readable type name that servers as an identifier (unrelated to the data type).
	 *
	 * @return he human-readable type name
	 */
	String getTypeName();

}
