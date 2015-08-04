package org.springframework.session.id;

import java.util.UUID;

import org.springframework.session.SessionIdStrategy;

/**
 * A {@link SessionIdStrategy} that uses a random {@link UUID}.
 * 
 * @author Art Gramlich
 *
 */
public class UUIDSessionIdStrategy implements SessionIdStrategy {

	/**
	 * Create a session id using a random UUID.
	 */
	public String createSessionId() {
		return UUID.randomUUID().toString();
	}

}
