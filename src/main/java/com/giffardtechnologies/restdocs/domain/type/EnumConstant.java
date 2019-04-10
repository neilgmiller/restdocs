package com.giffardtechnologies.restdocs.domain.type;

public class EnumConstant {
	private String value;
	private String longName;
	private String description;

	/**
	 * Returns the value of the key of this constant. This is a string regardless of the
	 * {@link KeyType}
	 *
	 * @return the value of key of this constant
	 */
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * Returns a human readable name for this enum constant. Should be camel-case.
	 *
	 * @return a human readable name for this enum constant
	 */
	public String getLongName() {
		return longName;
	}

	public void setLongName(String longName) {
		this.longName = longName;
	}

	/**
	 * Returns a description of the semantics of this enum constant. The is new limit to the length, what is practical
	 * for the documentation.
	 *
	 * @return a description of the semantics of this enum constant.
	 */
	public String getDescription() {
		return description == null ? longName : description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
