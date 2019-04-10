package com.giffardtechnologies.restdocs.domain;

import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.NamedType;
import com.giffardtechnologies.restdocs.domain.type.TypeSpec;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class NamedEnumeration extends TypeSpec implements NamedType {
	private String name;
	private String description;

	public NamedEnumeration() {
		setType(DataType.ENUM);
	}

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return "Enumeration: " + name;
	}

	@Override
	public String getTypeName() {
		return getName();
	}

}
