package com.giffardtechnologies.restdocs.domain.type

enum class KeyType(@JvmField val type: DataType) {
    INT(DataType.INT),
    LONG(DataType.LONG),
    STRING(DataType.STRING),
    ENUM(DataType.ENUM)

}
