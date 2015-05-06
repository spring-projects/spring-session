package org.springframework.session.id;

import org.springframework.session.Session;

/**
 * An interface to allow a choice of how a byte array-based id is encoded into a {@link String}. 
 * 
 * @author Art Gramlich
 */
public interface SessionIdEncoder {
	
	/**
	 * Encode the bytes into a {@link String} for use as a {@link Session} id.
	 * @param bytes the session id as a byte array
	 * @return the encoded session id.
	 */
	String encode(byte[] bytes);;
	
}