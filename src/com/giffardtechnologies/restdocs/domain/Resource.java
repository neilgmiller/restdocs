package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

public class Resource {
	private String uri;
	private String description = "";
	private ArrayList<Method> actions;
	
	public String getUri() {
		return uri;
	}
	
	public void setUri(String url) {
		this.uri = url;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public ArrayList<Method> getActions() {
		return actions;
	}
	
	public void setActions(ArrayList<Method> actions) {
		this.actions = actions;
	}
	
}
