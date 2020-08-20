package com.giffardtechnologies.restdocs.domain.type;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Field extends TypeSpec {
	private String name;
	private String longName = "";
	private String description;
	@Nullable
	@SerializedName("default")
	private String defaultValue;
	private boolean required = true;
	private NamedType mParent;
	@Nullable
	private List<String> sampleValues;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLongName() {
		return longName;
	}

	public void setLongName(String longName) {
		this.longName = longName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean hasDefaultValue() {
		return defaultValue != null;
	}

	// for velocity templating
	public boolean getHasDefaultValue() {
		return hasDefaultValue();
	}

	@Nullable
	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(@Nullable String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public NamedType getParent() {
		return mParent;
	}

	public void setParent(NamedType parent) {
		mParent = parent;
	}

	@Override
	public String toString() {
		return "Field{" +
				"name='" + name + '\'' +
				", longName='" + longName + '\'' +
				", description='" + description + '\'' +
				", required=" + required +
				'}';
	}

}
