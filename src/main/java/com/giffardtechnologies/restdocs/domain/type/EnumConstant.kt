package com.giffardtechnologies.restdocs.domain.type

class EnumConstant {
    /**
     * Returns the value of the key of this constant. This is a string regardless of the
     * [KeyType]
     *
     * @return the value of key of this constant
     */
    @JvmField
    var value: String? = null
    private var longName: String? = null
    var description: String? = null
        /**
         * Returns a description of the semantics of this enum constant. The is new limit to the length, what is practical
         * for the documentation.
         *
         * @return a description of the semantics of this enum constant.
         */
        get() = if (field == null) longName else field

    /**
     * Returns a human readable name for this enum constant. Should be camel-case.
     *
     * @return a human readable name for this enum constant
     */
    fun getLongName(): String? {
        return if (longName == null) value else longName
    }

    fun setLongName(longName: String?) {
        this.longName = longName
    }
}
