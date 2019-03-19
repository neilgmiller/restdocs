package com.giffardtechnologies.restdocs.domain;

import com.giffardtechnologies.restdocs.domain.type.TypeSpec;

public class Response extends TypeSpec {

	// TODO in the future added an encoding type option and way s to specify non-JSON responses

	private String description;

	public boolean getHasDescription() {
		return description != null && !description.isEmpty();
	}

	public String getDescription() {
		return description;
	}

}
