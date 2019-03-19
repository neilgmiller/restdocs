package com.giffardtechnologies.restdocs.domain;

import com.giffardtechnologies.restdocs.domain.type.Field;

import java.util.ArrayList;

public class Service {
	private String description;
	private Common common;
	private ArrayList<Method> methods;
	
	public static class Common {
		private ArrayList<Field> headers;
		private ArrayList<Field> parameters;
		
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
		
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
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
		return methods;
	}
	
	public void setMethods(ArrayList<Method> methods) {
		this.methods = methods;
	}
	
}
