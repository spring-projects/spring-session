package org.springframework.session.id;

import java.security.SecureRandom;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.session.SessionIdStrategy;

public class SecureRandomSessionIdStrategy implements SessionIdStrategy {
	
    private final Queue<SecureRandomGenerator> randomGenerators = new ConcurrentLinkedQueue<SecureRandomGenerator>();

    private int maxIterations = 100000;
    private int byteLength = 32;
    private SessionIdEncoder encoder = new HexSessionIdEncoder(true);
    
	public String createSessionId() {
		SecureRandomGenerator generator = randomGenerators.poll();
		if (generator == null) {
			generator = new SecureRandomGenerator();
		}
		byte[] bytes = new byte[byteLength];
		generator.generate(bytes);
		randomGenerators.add(generator);
		String id = encoder.encode(bytes);
		return id;
	}
	
	public void setByteLength(int byteLength) {
		this.byteLength = byteLength;
	}
	
	public void setEncoder(SessionIdEncoder encoder) {
		this.encoder = encoder;
	}

	private class SecureRandomGenerator {
		private SecureRandom secureRandom;
		private int iteration;
		
		private SecureRandomGenerator() {
			secureRandom = new SecureRandom();
			secureRandom.nextInt();
		}
		
		public void generate(byte[] bytes) {
			secureRandom.nextBytes(bytes);
			iteration++;
			if (iteration == maxIterations) {
				secureRandom.setSeed(secureRandom.nextLong());
				iteration = 0;
			}
		}
	}

}
