package com.giffardtechnologies.restdocs.domain.type

enum class BasicType {
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    BOOLEAN;

    fun asDataType(): DataType {
        return when (this) {
            INT -> DataType.INT
            LONG -> DataType.LONG
            FLOAT -> DataType.FLOAT
            DOUBLE -> DataType.DOUBLE
            STRING -> DataType.STRING
            BOOLEAN -> DataType.BOOLEAN
        }
        throw UnsupportedOperationException("Could not map $this to DataType")
    }
}
