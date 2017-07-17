package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

import com.google.gson.annotations.SerializedName;

public class Document {
	private String title;
	@SerializedName("data objects")
	private ArrayList<DataObject> dataObjects = new ArrayList<DataObject>();
	private Service service;
	
	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public boolean hasDataObjects() {
		return dataObjects != null && !dataObjects.isEmpty();
	}
	
	public boolean getHasDataObjects() {
		return hasDataObjects();
	}
	
	public ArrayList<DataObject> getDataObjects() {
		return dataObjects;
	}
	
	public void setDataObjects(ArrayList<DataObject> dataObjects) {
		this.dataObjects = dataObjects;
	}
	
	public Service getService() {
		return service;
	}
	
	public void setService(Service service) {
		this.service = service;
	}
	
}
