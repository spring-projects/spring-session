package org.springframework.session.id;

/**
 * {@link SessionIdEncoder} that encodes as hexidecimal digits.
 * 
 * @author Art Gramlich
 */
public class HexSessionIdEncoder implements SessionIdEncoder {
	
	private static final char[] LOWER_DIGITS = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	
	private static final char[] UPPER_DIGITS = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	
	private char[] digits;
	
	
	/**
	 * Creates a new encoder with lower case digits.
	 *
	 */
	public HexSessionIdEncoder() {
		this(false);
	}
	
	/**
	 * Creates a new encoder.
	 * 
	 * @param lowerCaseDigits - If true, lower case digits are used.
	 */
	public HexSessionIdEncoder(boolean lowerCaseDigits) {
		setLowerCaseDigits(lowerCaseDigits);
	}
	
	/**
	 * Indicates if upper or lower case digits should be used.
	 * 
	 * @param lowerCaseDigits - If true, lower case digits are used.
	 */
	public void setLowerCaseDigits(boolean lowerCaseDigits) {
		digits = lowerCaseDigits ? LOWER_DIGITS : UPPER_DIGITS;
	}
	
	/**
	 * Encode the session id as hex digits.
	 *  
	 * @param bytes the bytes representing a session id.
	 * @return The bytes as hexidecimal digits.
	 */
	public String encode(byte[] bytes) {
		StringBuilder id = new StringBuilder(bytes.length * 2);
		for (int i=0; i < bytes.length; i++) {
			id.append(digits[(0xF0 & bytes[i]) >>> 4]);
			id.append(digits[(0x0F & bytes[i])]);
		}
		return id.toString();
	}
}