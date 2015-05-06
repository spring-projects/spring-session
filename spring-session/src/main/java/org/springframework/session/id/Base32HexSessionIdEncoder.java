package org.springframework.session.id;

/**
 * {@link SessionIdEncoder} that encodes as a base32 extended hex with no padding.
 * 
 * @author Art Gramlich
 */
public class Base32HexSessionIdEncoder implements SessionIdEncoder {
	
	private static final char[] UPPER_DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUV".toCharArray();
	private static final char[] LOWER_DIGITS = "0123456789abcdefghijklmnopqrstuv".toCharArray();
	
	private char[] digits; 
	
	/**
	 * Creates a new encoder with lower case digits.
	 *
	 */
	public Base32HexSessionIdEncoder() {
		this(false);
	}
	
	/**
	 * Creates a new encoder.
	 * 
	 * @param lowerCaseDigits - If true, lower case digits are used.
	 */
	public Base32HexSessionIdEncoder(boolean lowerCaseDigits) {
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
	 * Encode the session id in a base32hex format.
	 *  
	 * @param bytes the bytes representing a session id.
	 * @return The session id as a string.
	 */
	public String encode(byte[] bytes) {
		int numberOfBytes = bytes.length;
		int numberOfTotalBits = (numberOfBytes * 8);
		int numberOfTotalDigits = (numberOfTotalBits / 5) + (numberOfTotalBits % 5 == 0 ? 0 : 1);
		StringBuilder id = new StringBuilder(numberOfTotalDigits);
		long fiveByteGroup;
		for (int i=0; i< numberOfBytes; i+=5) {
			int bytesInGroup = Math.min(numberOfBytes - i, 5);
			int digitsInGroup = ((bytesInGroup * 8) / 5) + (bytesInGroup == 5 ? 0 : 1);
			fiveByteGroup = 0;
			for (int j=0; j<5; j++) {
				byte b = (j >= bytesInGroup ? (byte)0 : bytes[i+j]);  
				long bits = (b & 0xffL) << (8*(4-j));
				fiveByteGroup = fiveByteGroup | bits;
			}
			for (int j=0; j<digitsInGroup; j++) {
				int digit = (int)(0x1fL & (fiveByteGroup >>> (5*(7-j))));
				id.append(digits[digit]);
			}
		}
		return id.toString();
	}
		
}