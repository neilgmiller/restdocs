package com.giffardtechnologies.restdocs.domain.type;

public enum FlagType {
	INT(DataType.INT),
	LONG(DataType.LONG),

	;

	private final DataType type;

	FlagType(DataType type) {
		this.type = type;
	}

	public DataType getType() {
		return type;
	}
}
