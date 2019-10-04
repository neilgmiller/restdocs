package com.giffardtechnologies.restdocs.domain;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// Most of these methods are used by velocity
@SuppressWarnings("unused")
public class Document {
	private String title;
	private ArrayList<NamedEnumeration> enumerations = new ArrayList<>();
	@SerializedName("data objects")
	private ArrayList<DataObject> dataObjects = new ArrayList<>();
	private Service service;

	private Map<String, DataObject> mDataObjectNames;
	private Map<String, NamedEnumeration> mEnumerationNames;

	public void buildMappings() {
		mDataObjectNames = new HashMap<>(dataObjects.size() * 2);
		for (DataObject dataObject : dataObjects) {
			mDataObjectNames.put(dataObject.getName(), dataObject);
			dataObject.linkFields();
		}
		mEnumerationNames = new HashMap<>(enumerations.size() * 2);
		for (NamedEnumeration enumeration : enumerations) {
			mEnumerationNames.put(enumeration.getName(), enumeration);
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

	public boolean hasEnumerations() {
		return enumerations != null && !enumerations.isEmpty();
	}

	public boolean getHasEnumerations() {
		return hasDataObjects();
	}

	public ArrayList<NamedEnumeration> getEnumerations() {
		return enumerations;
	}

	public NamedEnumeration getEnumerationByName(String name) {
		return mEnumerationNames.get(name);
	}

}
