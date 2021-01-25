package com.giffardtechnologies.restdocs.domain.type;

import com.giffardtechnologies.restdocs.domain.Document;
import com.giffardtechnologies.restdocs.domain.FieldElementList;
import com.giffardtechnologies.restdocs.domain.FieldListElement;
import com.giffardtechnologies.restdocs.domain.Restriction;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class TypeSpec {
	private DataType type;
	@SerializedName("typeref")
	private String typeRef;
	private KeyType key;
	private TypeSpec items;
	private ArrayList<Restriction> restrictions;
	@SerializedName("fields")
	private FieldElementList fieldElementList = new FieldElementList();
	private ArrayList<EnumConstant> values;

	public DataType getType() {
		return type;
	}

	public void setType(DataType type) {
		this.type = type;
	}

	public String getTypeRef() {
		return typeRef;
	}

	public void setTypeRef(String typeRef) {
		this.typeRef = typeRef;
	}

	// For velocity
	public boolean getIsTypeRef() {
		return typeRef != null;
	}

	public boolean isTypeRef() {
		return typeRef != null;
	}

	public TypeSpec getItems() {
		return items;
	}

	public void setItems(TypeSpec items) {
		this.items = items;
	}

	public KeyType getKey() {
		return key;
	}

	public void setKey(KeyType key) {
		this.key = key;
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

	public void setParentDocument(Document parentDocument) {
		fieldElementList.setParentDocument(parentDocument);
	}

	public boolean hasEnumValues() {
		return values != null && !values.isEmpty();
	}

	public boolean getHasEnumValues() {
		return hasEnumValues();
	}

	public ArrayList<EnumConstant> getValues() {
		return values;
	}

	public void setValues(ArrayList<EnumConstant> values) {
		this.values = values;
	}
}
