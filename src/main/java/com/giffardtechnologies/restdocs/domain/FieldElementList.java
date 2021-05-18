package com.giffardtechnologies.restdocs.domain;

import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.NamedType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class that manages a list of fields whether a straight list or a include
 */
public class FieldElementList {

	private ArrayList<FieldListElement> fieldListElements = new ArrayList<>();

	@Nullable
	private transient ArrayList<Field> fields;
	private transient Document parentDocument;
	private transient NamedType parentType;

	public ArrayList<Field> getFields() {
		if (fields == null && fieldListElements != null) {
			fields = new ArrayList<>();
			for (FieldListElement fieldListElement : fieldListElements) {
				if (fieldListElement instanceof Field) {
					fields.add((Field) fieldListElement);
				} else if (fieldListElement instanceof FieldListIncludeElement) {
					FieldListIncludeElement include = (FieldListIncludeElement) fieldListElement;
					DataObject includedObject = parentDocument.getDataObjectByName(include.getInclude());
					if (includedObject == null) {
						throw new IllegalStateException("Cannot find '" + include.getInclude());
					}
					if (include.getExcluding().isEmpty()) {
						fields.addAll(includedObject.getFields());
					} else {
						List<Field> fieldsToAdd = includedObject.getFields()
						                                        .stream()
						                                        .filter(field -> !include.getExcluding()
						                                                                 .contains(field.getLongName()))
						                                        .collect(Collectors.toList());
						this.fields.addAll(fieldsToAdd);
					}
				} else {
					throw new IllegalStateException("Unsupported element type: " + fieldListElement.getClass().getName());
				}
			}
			linkFields(fields);
		}
		return fields;
	}

	public void setFields(ArrayList<Field> fields) {
		this.fields = new ArrayList<>(fields);
		fieldListElements = new ArrayList<>(fields);
		linkFields(fields);
	}

	public boolean hasFields() {
		return fieldListElements != null && !fieldListElements.isEmpty();
	}

	public boolean getHasFields() {
		return fieldListElements != null && !fieldListElements.isEmpty();
	}

	public ArrayList<FieldListElement> getFieldListElements() {
		return fieldListElements;
	}

	public void setFieldListElements(ArrayList<FieldListElement> fieldListElements) {
		this.fieldListElements = new ArrayList<>(fieldListElements);
		fields = null;
	}

	private void linkFields(ArrayList<Field> fields) {
		for (Field field : fields) {
			field.setParent(parentType);
			field.setParentDocument(parentDocument);
		}
	}

	public void setParentDocument(Document parentDocument) {
		this.parentDocument = parentDocument;
	}

	public void setParentType(NamedType parentType) {
		this.parentType = parentType;
	}
}
