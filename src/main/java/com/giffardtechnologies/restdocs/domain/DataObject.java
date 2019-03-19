package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.NamedType;
import com.google.gson.annotations.SerializedName;

public class DataObject implements NamedType {
	private String name;
	private String description;
	private ArrayList<Field> fields;
	private Field discriminator;
	@SerializedName("child types")
	private ArrayList<DataObject> childTypes;
	@SerializedName("discriminator value")
	private String discriminatorValue;
	
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
	
	public ArrayList<Field> getFields() {
		return fields;
	}
	
	public void setFields(ArrayList<Field> fields) {
		this.fields = fields;
	}
	
	public boolean hasFields() {
		return fields != null && !fields.isEmpty();
	}
	
	public boolean getHasFields() {
		return fields != null && !fields.isEmpty();
	}
	
	public Field getDiscriminator() {
		return discriminator;
	}
	
	public void setDiscriminator(Field discriminator) {
		this.discriminator = discriminator;
	}
	
	public ArrayList<DataObject> getChildTypes() {
		return childTypes;
	}
	
	public void setChildTypes(ArrayList<DataObject> childTypes) {
		this.childTypes = childTypes;
	}
	
	public String getDiscriminatorValue() {
		return discriminatorValue;
	}
	
	public void setDiscriminatorValue(String discriminatorValue) {
		this.discriminatorValue = discriminatorValue;
	}
	
	public boolean hasChildren() {
		return childTypes != null && !childTypes.isEmpty() && discriminator != null;
	}
	
	public boolean getHasChildren() {
		return childTypes != null && !childTypes.isEmpty() && discriminator != null;
	}
	
	@Override
	public String toString() {
		return name;
	}

	@Override
	public String getTypeName() {
		return getName();
	}

	public void linkFields() {
		for (Field field : fields) {
			field.setParent(this);
		}
	}
}
