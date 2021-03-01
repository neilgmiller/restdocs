package com.giffardtechnologies.restdocs.domain;

import com.giffardtechnologies.restdocs.domain.type.NamedType;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Most of these methods are used by velocity
@SuppressWarnings("unused")
public class Document {
	private String title;
	private final ArrayList<NamedEnumeration> enumerations = new ArrayList<>();
	@SerializedName("data objects")
	private final ArrayList<DataObject> dataObjects = new ArrayList<>();
	@Nullable
	private transient List<DataObject> visibleDataObjects;
	private Service service;

	private Map<String, DataObject> mDataObjectNames;
	private Map<String, NamedEnumeration> mEnumerationNames;

	public void buildMappings() {
		mDataObjectNames = new HashMap<>(dataObjects.size() * 2);
		for (DataObject dataObject : dataObjects) {
			mDataObjectNames.put(dataObject.getName(), dataObject);
			dataObject.setParent(this);
		}
		mEnumerationNames = new HashMap<>(enumerations.size() * 2);
		for (NamedEnumeration enumeration : enumerations) {
			mEnumerationNames.put(enumeration.getName(), enumeration);
		}
		if (service != null) {
			service.setParentDocument(this);
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
		visibleDataObjects = null;
		this.dataObjects.clear();
		this.dataObjects.addAll(dataObjects);
	}

	public List<DataObject> getVisibleDataObjects() {
		if (visibleDataObjects == null) {
			visibleDataObjects = dataObjects.stream().filter(o -> !o.isHidden()).collect(Collectors.toList());
		}
		return visibleDataObjects;
	}

	public boolean hasVisibleDataObjects() {
		return !getVisibleDataObjects().isEmpty();
	}

	public boolean getHasVisibleDataObjects() {
		return hasVisibleDataObjects();
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

	public NamedType getTypeByName(String name) {
		DataObject dataObject = mDataObjectNames.get(name);
		NamedEnumeration namedEnumeration = mEnumerationNames.get(name);
		if (dataObject == null && namedEnumeration == null) {
			throw new IllegalStateException("Type reference to undefined type: " + name + ".");
		} else if (dataObject != null && namedEnumeration != null) {
			throw new IllegalStateException("Ambiguous type reference: " + name + ".");
		}

		if (dataObject == null) {
			return namedEnumeration;
		} else {
			return dataObject;
		}
	}

}
