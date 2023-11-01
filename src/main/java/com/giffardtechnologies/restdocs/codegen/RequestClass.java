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
		return mMethod.id;
	}

	public void setId(int id) {
		mMethod.id = id;
	}

	public String getName() {
		return mMethod.name;
	}

	public void setName(String name) {
		mMethod.name = name;
	}

	public boolean getHasDescription() {
		return mMethod.getHasDescription();
	}

	public String getDescription() {
		return mMethod.description;
	}

	public void setDescription(String description) {
		mMethod.description = description;
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
