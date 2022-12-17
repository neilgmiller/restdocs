package com.giffardtechnologies.restdocs.storage.type

enum class KeyType(val type: DataType) {
    INT(DataType.INT), LONG(DataType.LONG), STRING(DataType.STRING), ENUM(DataType.ENUM);
}