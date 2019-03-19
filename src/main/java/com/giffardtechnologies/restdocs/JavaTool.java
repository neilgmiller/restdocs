package com.giffardtechnologies.restdocs;

import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.Document;
import com.giffardtechnologies.restdocs.domain.Response;
import com.giffardtechnologies.restdocs.domain.Restriction;
import com.giffardtechnologies.restdocs.domain.type.DataType;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.type.KeyType;
import com.giffardtechnologies.restdocs.domain.type.NamedType;
import com.giffardtechnologies.restdocs.domain.type.TypeSpec;
import com.google.common.base.CaseFormat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

@SuppressWarnings({"unused", "WeakerAccess"})
public class JavaTool {

	private final Document mDocument;

	public JavaTool(Document document) {
		super();
		mDocument = document;
	}
	
	public String fieldClass(Field field) {
		return fieldClass(field, false);
	}

	public String fieldClass(Field field, boolean convertIntBoolean) {
		Objects.requireNonNull(field, "A field is required");
		String typeStr = getTypeString(field, field.isRequired(), convertIntBoolean);
		return typeStr;
	}

	public String getTypeString(TypeSpec typeSpec, boolean required) {
		return getTypeString(typeSpec, required, false );
	}

	public String getTypeString(TypeSpec typeSpec, boolean required, boolean convertIntBoolean) {
		DataType type = typeSpec.getType();
		if (type != null) {
			switch (type) {
				case INT:
					if (convertIntBoolean && hasBooleanRestriction(typeSpec)) {
						return required ? "boolean" : "Boolean";
					}
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
					if (hasBooleanRestriction(typeSpec)) {
						return required ? "boolean" : "Boolean";
					}
					return "String";
				case DATE:
					return "LocalDate";
				case COLLECTION:
					return "Map<" + getKeyTypeString(typeSpec.getKey()) + ", " + getTypeString(typeSpec.getItems(), false, convertIntBoolean) + ">";
				case ENUM:
					if (typeSpec instanceof Field) {
						Field field = (Field) typeSpec;
						return "FutureProofEnumContainer<" + fieldToClassStyle(field) + ">";
					} else {
						throw new IllegalStateException("Raw enum type specified, cannot generate name.");
					}
				case ARRAY:
					// pass required false, since we can't use primitives
					return "List<" + getTypeString(typeSpec.getItems(), false, convertIntBoolean) + ">";
			}
		} else if (typeSpec.getTypeRef() != null) {
			DataObject dataObject = mDocument.getDataObjectByName(typeSpec.getTypeRef());
			if (dataObject == null) {
				throw new IllegalStateException("Type reference to undefined type.");
			}
			// TODO handle common enums
			return typeSpec.getTypeRef();
		}
		return null;
	}

	private String getKeyTypeString(KeyType key) {
		switch (key.getType()) {
			case INT:
				return "Integer";
			case LONG:
				return "Long";
			case FLOAT:
				return "Float";
			case DOUBLE:
				return "Double";
			case STRING:
				return "String";
			case DATE:
				return "LocalDate";
			default:
				throw new IllegalArgumentException("Unsupported key type");
		}
	}

	public boolean hasBooleanRestriction(TypeSpec typeSpec) {
		if (typeSpec.getRestrictions() != null) {
			for (Restriction restriction : typeSpec.getRestrictions()) {
				if (restriction.getRestriction().equalsIgnoreCase("boolean")) {
					return true;
				}
			}
		}
		return false;
	}

	public String toGetterStyle(String input) {
		return input.replaceAll("Id$", "ID");
	}

	public String toConstantStyle(String input) {
		input = input.replaceAll("ID", "Id");
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, input);
	}

	public String fieldNameToClassStyle(String input) {
		input = input.replaceAll("ID", "Id");
		String classStyle = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, input);
		classStyle = classStyle.replaceAll("Id[A-Z]", "ID");
		return classStyle;
	}

	public String fieldToClassStyle(Field field) {
		NamedType parent = field.getParent();
		if (parent != null) {
			return parent.getTypeName() + fieldNameToClassStyle(field.getLongName());
		} else {
			return fieldNameToClassStyle(field.getLongName());
		}
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
			} else if (response.getTypeRef() != null) {
				return response.getTypeRef();
			}
		}
		return null;
	}

}
