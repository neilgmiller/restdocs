package com.giffardtechnologies.restdocs.codegen;

import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.FieldElementList;
import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.Field;

import java.util.ArrayList;

public class JavaDataObject {

	private String name;
	private FieldElementList fieldElementList = new FieldElementList();

	public static JavaDataObject fromDataObject(DataObject dataObject) {
		return new JavaDataObject(dataObject.getName(), dataObject.getFieldElementList());
	}

	public static JavaDataObject fromField(Field field) {
		if (field.getType() != DataType.OBJECT) {
			throw new IllegalArgumentException(String.format("Field '%s' is not an Object", field.getLongName()));
		}
		return new JavaDataObject(field.getLongName(), field.getFieldElementList());
	}

	public JavaDataObject() {
	}

	public JavaDataObject(String name, FieldElementList fieldElementList) {
		this.name = name;
		this.fieldElementList = fieldElementList;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ArrayList<Field> getFields() {
		return fieldElementList.getFields();
	}

	public void setFields(ArrayList<Field> fields) {
		fieldElementList.setFields(fields);
	}

}