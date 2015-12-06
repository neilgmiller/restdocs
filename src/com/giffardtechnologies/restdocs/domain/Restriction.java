package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

public class Restriction {
	private String restriction;
	private String value = "";
	private ArrayList<Object> values;
	
	public String getRestriction() {
		return restriction;
	}
	
	public void setRestriction(String restriction) {
		this.restriction = restriction;
	}
	
	public String getValue() {
		return value;
	}
	
	public void setValue(String value) {
		this.value = value;
	}
	
	public ArrayList<Object> getValues() {
		return values;
	}
	
	public void setValues(ArrayList<Object> values) {
		this.values = values;
	}
	
	public boolean getHasMultipleValues() {
		return values != null && !values.isEmpty();
	}
}
