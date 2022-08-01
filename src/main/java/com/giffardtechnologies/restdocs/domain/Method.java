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
	private String path;
	@SerializedName("protocols allowed")
	private ArrayList<String> protocolsAllowed = new ArrayList<>();
	private Integer id;
	private String name = "";
	private String description = "";
	@SerializedName("authentication required")
	private boolean authenticationRequired = true;
	private ArrayList<Field> headers = new ArrayList<>();
	private FieldElementList parameters = new FieldElementList();
	@SerializedName("request body")
	private RequestBody requestBody;
	private Response response;
	@SerializedName("successful codes")
	private ArrayList<String> successCodes = new ArrayList<>();
	@SerializedName("failure codes")
	private ArrayList<String> failureCodes = new ArrayList<>();
	
	public Method() {
		super();
		protocolsAllowed.add("HTTP");
	}

	public void setParent(Service service) {
		if (response != null) {
			response.setParentDocument(service.getParentDocument());
		}
		parameters.setParentDocument(service.getParentDocument());
	}

	public String getMethodString() {
		return method == null ? "null" : method.name();
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public boolean getHasID() {
		return id != null;
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

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
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

	public boolean isAuthenticationRequired() {
		return authenticationRequired;
	}

	public boolean getIsAuthenticationRequired() {
		return isAuthenticationRequired();
	}

	public void setAuthenticationRequired(boolean authenticationRequired) {
		this.authenticationRequired = authenticationRequired;
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
		return parameters.getHasFields();
	}
	
	public ArrayList<Field> getParameters() {
		return parameters.getFields();
	}
	
	public void setParameters(ArrayList<Field> parameters) {
		this.parameters.setFields(parameters);
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
