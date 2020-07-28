package com.sap.exception;

public class IgnoreFileTypeException extends Exception {
	
	private static final long serialVersionUID = -8006630353359097191L;
	protected String message;
	
	public IgnoreFileTypeException(String defualtSystemException, Exception e) {
		e.printStackTrace();	
		this.message = defualtSystemException;
	}
	
	public IgnoreFileTypeException(String message) {
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

}
