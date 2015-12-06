package com.giffardtechnologies.restdocs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;

import com.giffardtechnologies.restdocs.domain.DataObject;
import com.giffardtechnologies.restdocs.domain.Document;

public class LinkTool {
	
	private final Document mDocument;
	private final Set<String> mDataObjectNames;
	
	public LinkTool(Document document) {
		super();
		mDocument = document;
		
		ArrayList<DataObject> dataObjects = mDocument.getDataObjects();
		mDataObjectNames = new HashSet<String>(dataObjects.size() * 2);
		for (DataObject dataObject : dataObjects) {
			mDataObjectNames.add(dataObject.getName());
		}
	}
	
	public String type(String string) {
		if (string == null) {
			return null;
		}
		final String escapeHtml = StringEscapeUtils.escapeHtml(String.valueOf(string));
		Pattern typeLinkPattern = Pattern.compile("&lt;.+?&gt;");
		Matcher matcher = typeLinkPattern.matcher(escapeHtml);
		StringBuffer builder = new StringBuffer();
		while (matcher.find()) {
			final String typeLink = matcher.group();
			String typeName = typeLink.substring(4, typeLink.length() - 4);
			if (mDataObjectNames.contains(typeName)) {
				matcher.appendReplacement(builder, "<a href=\"#" + typeName + "\">" + typeName + "</a>");
			} else {
				matcher.appendReplacement(builder, typeLink);
			}
		}
		matcher.appendTail(builder);
		return builder.toString();
	}
}
