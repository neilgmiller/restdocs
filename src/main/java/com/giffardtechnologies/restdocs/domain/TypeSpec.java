package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

public class TypeSpec {
	private DataType type;
	private String typeref;
	private TypeSpec items;
	private ArrayList<Restriction> restrictions;
	private ArrayList<Field> fields;

	public DataType getType() {
		return type;
	}

	public void setType(DataType type) {
		this.type = type;
	}

	public String getTyperef() {
		return typeref;
	}

	public void setTyperef(String typeref) {
		this.typeref = typeref;
	}

	// For velocity
	public boolean getIsTypeRef() {
		return typeref != null;
	}

	public boolean isTypeRef() {
		return typeref != null;
	}

	public TypeSpec getItems() {
		return items;
	}

	public void setItems(TypeSpec items) {
		this.items = items;
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

}
