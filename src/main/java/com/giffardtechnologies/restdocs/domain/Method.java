package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

import com.giffardtechnologies.restdocs.domain.type.Field;
import com.google.gson.annotations.SerializedName;

public class Method {
	public enum HTTPMethod {
		GET,
		PUT,
		POST,
		DELETE,
		HEAD,
		OPTIONS,
		TRACE,
		CONNECT
	}
	
	private HTTPMethod method;
	@SerializedName("protocols allowed")
	private ArrayList<String> protocolsAllowed = new ArrayList<>();
	private int id;
	private String name = "";
	private String description = "";
	private ArrayList<Field> headers;
	private ArrayList<Field> parameters;
	@SerializedName("request body")
	private RequestBody requestBody;
	private Response response;
	@SerializedName("successful codes")
	private ArrayList<String> successCodes;
	@SerializedName("failure codes")
	private ArrayList<String> failureCodes;
	
	public Method() {
		super();
		protocolsAllowed.add("HTTP");
	}
	
	public String getMethodString() {
		return method == null ? "null" : method.name();
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public HTTPMethod getMethod() {
		return method;
	}

	public void setMethod(HTTPMethod method) {
		this.method = method;
	}
	
	public ArrayList<String> getProtocolsAllowed() {
		return protocolsAllowed;
	}
	
	public void setProtocolsAllowed(ArrayList<String> protocolsAllowed) {
		this.protocolsAllowed = protocolsAllowed;
	}
	
	public boolean getHasDescription() {
		return description != null && !description.isEmpty();
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
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
	
	public boolean getHasParameters() {
		return parameters != null && !parameters.isEmpty();
	}
	
	public ArrayList<Field> getParameters() {
		return parameters;
	}
	
	public void setParameters(ArrayList<Field> parameters) {
		this.parameters = parameters;
	}
	
	public boolean getHasRequestBody() {
		return requestBody != null;
	}
	
	public RequestBody getRequestBody() {
		return requestBody;
	}
	
	public void setRequestBody(RequestBody requestBody) {
		this.requestBody = requestBody;
	}
	
	public Response getResponse() {
		return response;
	}
	
	public void setResponse(Response response) {
		this.response = response;
	}
	
	public ArrayList<String> getSuccessCodes() {
		return successCodes;
	}
	
	public void setSuccessCodes(ArrayList<String> successCodes) {
		this.successCodes = successCodes;
	}
	
	public ArrayList<String> getFailureCodes() {
		return failureCodes;
	}
	
	public void setFailureCodes(ArrayList<String> failureCodes) {
		this.failureCodes = failureCodes;
	}
	
}
