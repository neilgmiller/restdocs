package com.giffardtechnologies.restdocs.codegen;

import com.giffardtechnologies.restdocs.domain.type.Field;
import com.giffardtechnologies.restdocs.domain.Method;

import java.util.ArrayList;

public class RequestClass {

	private final Method mMethod;

	public RequestClass(Method method) {
		mMethod = method;
	}


	public int getId() {
		return mMethod.getId();
	}

	public void setId(int id) {
		mMethod.setId(id);
	}

	public String getName() {
		return mMethod.getName();
	}

	public void setName(String name) {
		mMethod.setName(name);
	}

	public boolean getHasDescription() {
		return mMethod.getHasDescription();
	}

	public String getDescription() {
		return mMethod.getDescription();
	}

	public void setDescription(String description) {
		mMethod.setDescription(description);
	}

	public boolean getHasParameters() {
		return mMethod.getHasParameters();
	}

	public ArrayList<Field> getParameters() {
		return mMethod.getParameters();
	}

	public void setParameters(ArrayList<Field> parameters) {
		mMethod.setParameters(parameters);
	}
}
