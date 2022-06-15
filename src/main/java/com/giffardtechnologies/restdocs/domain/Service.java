package com.giffardtechnologies.restdocs.domain;

import com.giffardtechnologies.restdocs.domain.type.Field;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;

public class Service {
	private String description;
	@SerializedName("base path")
	private String basePath;
	private Common common;
	private ArrayList<Method> methods;
	private Document mParentDocument;

	public void setParentDocument(Document parentDocument) {
		mParentDocument = parentDocument;
		for (Method method : methods) {
			method.setParent(this);
		}
	}

	public Document getParentDocument() {
		return mParentDocument;
	}

	public static class Common {
		private ArrayList<Field> headers;
		private ArrayList<Field> parameters;
		@SerializedName("response objects")
		private ArrayList<DataObject> responseDataObjects = new ArrayList<>();

		public boolean hasHeaders() {
			return headers != null && !headers.isEmpty();
		}
		
		public boolean getHasHeaders() {
			return hasHeaders();
		}
		
		public ArrayList<Field> getHeaders() {
			return headers;
		}
		
		public void setHeaders(ArrayList<Field> headers) {
			this.headers = headers;
		}
		
		public boolean hasParameters() {
			return parameters != null && !parameters.isEmpty();
		}
		
		public boolean getHasParameters() {
			return hasParameters();
		}
		
		public ArrayList<Field> getParameters() {
			return parameters;
		}
		
		public void setParameters(ArrayList<Field> parameters) {
			this.parameters = parameters;
		}

		public boolean hasResponseDataObjects() {
			return responseDataObjects != null && !responseDataObjects.isEmpty();
		}

		public boolean getHasResponseDataObjects() {
			return hasResponseDataObjects();
		}

		public ArrayList<DataObject> getResponseDataObjects() {
			return responseDataObjects;
		}

		public void setResponseDataObjects(ArrayList<DataObject> responseDataObjects) {
			this.responseDataObjects = responseDataObjects;
		}
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}

	public boolean hasCommon() {
		return common != null;
	}
	
	public boolean getHasCommon() {
		return hasCommon();
	}
	
	public Common getCommon() {
		return common;
	}
	
	public void setCommon(Common common) {
		this.common = common;
	}
	
	public ArrayList<Method> getMethods() {
		return methods == null ? new ArrayList<>(0) : methods;
	}
	
	public void setMethods(ArrayList<Method> methods) {
		this.methods = methods;
	}
	
}
