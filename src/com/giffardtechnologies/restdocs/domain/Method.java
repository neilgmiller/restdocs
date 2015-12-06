package com.giffardtechnologies.restdocs.domain;

import java.util.ArrayList;

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
	
	private String method;
	@SerializedName("protocols allowed")
	private ArrayList<String> protocolsAllowed = new ArrayList<>();
	private String description = "";
	private ArrayList<Field> headers;
	private ArrayList<Field> parameters;
	@SerializedName("request body")
	private RequestBody requestBody;
	private String response;
	@SerializedName("successful codes")
	private ArrayList<String> successCodes;
	@SerializedName("failure codes")
	private ArrayList<String> failureCodes;
	
	public Method() {
		super();
		protocolsAllowed.add("HTTP");
	}
	
	public String getMethod() {
		return method;
	}
	
	public void setMethod(String method) {
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
	
	public String getResponse() {
		return response;
	}
	
	public void setResponse(String response) {
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
