package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.NamedType;
import com.google.gson.annotations.SerializedName;

public class DataObject implements NamedType {
	private String name;
	private String description;
	@SerializedName("fields")
	private FieldElementList fieldElementList = new FieldElementList();
	private Field discriminator;
	@SerializedName("child types")
	private ArrayList<DataObject> childTypes;
	@SerializedName("discriminator value")
	private String discriminatorValue;

	private transient Document parent;

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public DataType getType() {
		return DataType.OBJECT;
	}

	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public ArrayList<Field> getFields() {
		return fieldElementList.getFields();
	}
	
	public void setFields(ArrayList<Field> fields) {
		fieldElementList.setFields(fields);
	}
	
	public boolean hasFields() {
		return fieldElementList.hasFields();
	}
	
	public boolean getHasFields() {
		return fieldElementList.getHasFields();
	}

	public ArrayList<FieldListElement> getFieldListElements() {
		return fieldElementList.getFieldListElements();
	}

	public void setFieldListElements(ArrayList<FieldListElement> fieldListElements) {
		fieldElementList.setFieldListElements(fieldListElements);
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

	public void setParent(Document parent) {
		this.parent = parent;
		fieldElementList.setParentDocument(parent);
		fieldElementList.setParentType(this);
	}

}
