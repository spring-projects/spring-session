package org.springframework.session;

/**
 * An interface 
 * 
 * @author Art Gramlich
 */
public interface SessionIdStrategy {

	/**
	 * Creates a new session id.
	 * 
	 * @return the new session id
	 */
	String createSessionId();
	
}
