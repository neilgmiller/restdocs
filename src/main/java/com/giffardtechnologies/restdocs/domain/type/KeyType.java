package com.giffardtechnologies.restdocs.domain.type;

public enum KeyType {
	INT(DataType.INT),
	LONG(DataType.LONG),
	STRING(DataType.STRING),

	;

	private final DataType type;

	KeyType(DataType type) {
		this.type = type;
	}

	public DataType getType() {
		return type;
	}
}
