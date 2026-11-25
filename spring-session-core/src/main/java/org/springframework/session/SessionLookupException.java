package org.springframework.session;

public class SessionLookupException extends RuntimeException {

	private final String sessionId;

	public SessionLookupException(String sessionId, Throwable cause) {
		super("Failed to retrieve session", cause);
		this.sessionId = sessionId;
	}

	public String getSessionId() {
		return this.sessionId;
	}

}
