package com.giffardtechnologies.restdocs

import com.giffardtechnologies.restdocs.domain.Document
import org.apache.commons.text.StringEscapeUtils
import java.util.regex.Pattern

class LinkTool(private val mDocument: Document) {
    private val mDataObjectNames: MutableSet<String>

    init {
        val dataObjects = mDocument.dataObjects
        val enumerations = mDocument.enumerations
        mDataObjectNames = HashSet((dataObjects.size + enumerations.size) * 2)
        for (dataObject in dataObjects) {
            mDataObjectNames.add(dataObject.name)
        }
        for (enumeration in enumerations) {
            mDataObjectNames.add(enumeration.name)
        }
        if (mDocument.service != null && mDocument.service.hasCommon()) {
            for (dataObject in mDocument.service.common.responseDataObjects) {
                mDataObjectNames.add(dataObject.name)
            }
        }
    }

    fun type(string: String?): String? {
        if (string == null) {
            return null
        }
        val escapeHtml = StringEscapeUtils.escapeHtml4(string)
        val typeLinkPattern = Pattern.compile("&lt;.+?&gt;")
        val matcher = typeLinkPattern.matcher(escapeHtml)
        val builder = StringBuffer()
        while (matcher.find()) {
            val typeLink = matcher.group()
            val typeName = typeLink.substring(4, typeLink.length - 4)
            if (mDataObjectNames.contains(typeName)) {
                matcher.appendReplacement(builder, "<a href=\"#$typeName\">$typeName</a>")
            } else {
                matcher.appendReplacement(builder, typeLink)
            }
        }
        matcher.appendTail(builder)
        return builder.toString()
    }

    fun typeSimple(typeName: String?): String? {
        if (typeName == null) {
            return null
        }
        return if (mDataObjectNames.contains(typeName)) {
            "<a href=\"#$typeName\">$typeName</a>"
        } else {
            typeName
        }
    }
}
