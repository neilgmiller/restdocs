package com.giffardtechnologies.restdocs;

import com.giffardtechnologies.restdocs.domain.*;
import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.TypeSpec;

import java.util.*;

public class PlainTextTool {

	private final Document mDocument;
	private final Set<String> mDataObjectNames;

	public PlainTextTool(Document document) {
		super();
		mDocument = document;
		
		ArrayList<DataObject> dataObjects = mDocument.getDataObjects();
		mDataObjectNames = new HashSet<String>(dataObjects.size() * 2);
		for (DataObject dataObject : dataObjects) {
			mDataObjectNames.add(dataObject.getName());
		}
	}
	
	public String fieldClass(Field field) {
		Objects.requireNonNull(field, "A field is required");
		String typeStr = getTypeString(field, field.isRequired());
		return typeStr;
	}

	private String getTypeString(TypeSpec typeSpec, boolean required) {
		DataType type = typeSpec.getType();
		if (type != null) {
			switch (type) {
				case INT:
				case LONG:
				case FLOAT:
				case DOUBLE:
				case STRING:
				case BOOLEAN:
				case OBJECT:
				case DATE:
					return type.toString().toLowerCase(Locale.US);
				case COLLECTION:
					return "collection of " + typeSpec.getKey().getType().toString().toLowerCase(Locale.US) + " to " + getTypeString(typeSpec.getItems(), false);
				case ENUM:
					return "enum keyed on " + typeSpec.getKey().getType().toString().toLowerCase(Locale.US);
				case ARRAY:
					// pass required false, since we can't use primitives
					return "array of " + getTypeString(typeSpec.getItems(), false);
				case BITSET:
					return "bitset in a " + typeSpec.getFlagType().getType().toString().toLowerCase(Locale.US);
			}
		} else if (typeSpec.getTypeRef() != null) {
			return typeSpec.getTypeRef();
		}
		return null;
	}

}
