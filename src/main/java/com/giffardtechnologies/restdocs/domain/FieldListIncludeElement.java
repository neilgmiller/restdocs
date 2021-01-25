package com.giffardtechnologies.restdocs.domain;

/**
 * A class for including the fields of data object inline with the a list of fields
 */
public class FieldListIncludeElement implements FieldListElement {
	/**
	 * A reference to a DataObject, all fields of that object will be included
	 */
	private String include;

	public String getInclude() {
		return include;
	}

	public void setInclude(String include) {
		this.include = include;
	}
}
