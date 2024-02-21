package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.storage.Document
import org.apache.commons.text.StringEscapeUtils
import java.util.regex.Pattern

@Suppress("unused")
class LinkTool(document: Document) {
    private val dataObjectNames: MutableSet<String>

    init {
        val dataObjects = document.dataObjects
        val enumerations = document.enumerations
        dataObjectNames = HashSet((dataObjects.size + enumerations.size) * 2)
        for (dataObject in dataObjects) {
            dataObjectNames.add(dataObject.name)
        }
        for (enumeration in enumerations) {
            dataObjectNames.add(enumeration.name)
        }
        if (document.service?.common != null) {
            for (dataObject in document.service.common.responseDataObjects) {
                dataObjectNames.add(dataObject.name)
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
            if (dataObjectNames.contains(typeName)) {
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
        return if (dataObjectNames.contains(typeName)) {
            "<a href=\"#$typeName\">$typeName</a>"
        } else {
            typeName
        }
    }
}
