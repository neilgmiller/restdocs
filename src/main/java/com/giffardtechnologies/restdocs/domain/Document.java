package com.giffardtechnologies.restdocs.domain;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Document {
	private String title;
	@SerializedName("data objects")
	private ArrayList<DataObject> dataObjects = new ArrayList<DataObject>();
	private Service service;

	private Map<String, DataObject> mDataObjectNames;

	public void buildMappings() {
		mDataObjectNames = new HashMap<>(this.dataObjects.size() * 2);
		for (DataObject dataObject : this.dataObjects) {
			mDataObjectNames.put(dataObject.getName(), dataObject);
			dataObject.linkFields();
		}
	}

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

	public DataObject getDataObjectByName(String name) {
		return mDataObjectNames.get(name);
	}
	
}
