package com.giffardtechnologies.restdocs.domain;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class RequestBody {
	private String description;
	@SerializedName("content types")
	private ArrayList<String> contentTypes;
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}

	public ArrayList<String> getContentTypes() {
		return contentTypes;
	}

	public void setContentTypes(ArrayList<String> contentTypes) {
		this.contentTypes = contentTypes;
	}
}
