package com.giffardtechnologies.restdocs.domain.type;

public enum BasicType {
	INT, LONG, FLOAT, DOUBLE, STRING, BOOLEAN

	;
	public DataType asDataType() {
		switch (this) {
			case INT:
				return DataType.INT;
			case LONG:
				return DataType.LONG;
			case FLOAT:
				return DataType.FLOAT;
			case DOUBLE:
				return DataType.DOUBLE;
			case STRING:
				return DataType.STRING;
			case BOOLEAN:
				return DataType.BOOLEAN;
		}
		throw new UnsupportedOperationException("Could not map " + this + " to DataType");
	}
}
