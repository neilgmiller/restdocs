package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

public class Field {
	private String name;
	private String longName = "";
	private String description;
	private boolean required = true;
	private String type;
	private ArrayList<Restriction> restrictions;
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public String getLongName() {
		return longName;
	}

	public void setLongName(String longName) {
		this.longName = longName;
	}

	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public boolean isRequired() {
		return required;
	}
	
	public void setRequired(boolean required) {
		this.required = required;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public ArrayList<Restriction> getRestrictions() {
		return restrictions;
	}
	
	public void setRestrictions(ArrayList<Restriction> restrictions) {
		this.restrictions = restrictions;
	}
	
	public boolean getHasRestrictions() {
		return restrictions != null && !restrictions.isEmpty();
	}
	
}
