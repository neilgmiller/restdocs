package com.giffardtechnologies.restdocs;

import com.giffardtechnologies.restdocs.domain.*;
import com.google.common.base.CaseFormat;
import org.apache.commons.lang.StringEscapeUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class JavaTool {

	private final Document mDocument;
	private final Set<String> mDataObjectNames;

	public JavaTool(Document document) {
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
					return required ? "int" : "Integer";
				case LONG:
					return required ? "long" : "Long";
				case FLOAT:
					return required ? "float" : "Float";
				case DOUBLE:
					return required ? "double" : "Double";
				case OBJECT:
					return "Object";
				case STRING:
					return "String";
				case DATE:
					return "LocalDate";
				case ARRAY:
					// pass required false, since we can't use primitives
					return "List<" + getTypeString(typeSpec.getItems(), false) + ">";
			}
		} else if (typeSpec.getTyperef() != null) {
			return typeSpec.getTyperef();
		}
		return null;
	}

	public String toConstantStyle(String input) {
		input = input.replaceAll("ID", "Id");
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, input);
	}

	public String responseClass(@Nonnull String requestClassName, @Nullable Response response) {
		Objects.requireNonNull(requestClassName, "A requestClassName is required");
		if (response == null) {
			return null;
		} else {
			DataType type = response.getType();
			if (type != null) {
				if (type == DataType.OBJECT) {
					return requestClassName.replaceAll("Request$", "Response");
				} else {
					// pass required false, since we can't use primitives
					return getTypeString(response, false);
				}
			} else if (response.getTyperef() != null) {
				return response.getTyperef();
			}
		}
		return null;
	}

}
