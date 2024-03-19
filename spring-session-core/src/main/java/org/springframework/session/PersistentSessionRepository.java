package org.springframework.session;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.crypto.SecretKey;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link SessionRepository} implementation that encrypts session information and stores
 * it in the session id. It uses the
 * <a href="https://datatracker.ietf.org/doc/html/rfc5116">AES with Galois/Counter Mode
 * (AES-GCM)</a> to encrypt the {@link PersistentSession#getExpireAt()} and the username
 * retrieved from the {@link SecurityContext} separated by a {@link #SEPARATOR}. The value
 * before encryption looks like: <pre>1703771570037:user</pre> and after encryption:
 * <pre>ac4609094c60c4daa8e85047ce10a36ce04f8cf0b6bf0547ce71ec9e04afcf76d533132c995ec90edd2b0f30021b24b7e245</pre>
 * <p>
 * The session id later on be decrypted and the session contents restored. Supports
 * clustering if all cluster members share the same encryption key.
 * <p>
 * Note that, by default, this implementation only serializes the username found in the
 * session by using a {@link PersistentSessionPrincipalNameResolver}, all attributes will
 * be lost during serialization. If you need the session attributes to be persisted during
 * serialization/deserialization, this implementation might not be the best choice.
 * <p>
 * To prevent frequent updates of the session ID with each request, the default behavior
 * is to perform the update at one-minute intervals, as defined by the
 * {@link #updateSessionIdAfter} field. The logic performed to decide if a new id is
 * needed is represented by the following pseudocode: <pre>
 *   lastUpdate = expireAt - maxInactiveInterval
 *   if ((lastUpdate + updateSessionIdAfter) < currentTime) {
 *       // generates a new id
 *   }
 * </pre>
 *
 * @author Marcus da Coregio
 * @since 3.3
 */
public final class PersistentSessionRepository
		implements SessionRepository<PersistentSessionRepository.PersistentSession> {

	private static final String SEPARATOR = ":";

	private final AesBytesEncryptor encryptor;

	private final PersistentSessionPrincipalNameResolver principalNameResolver;

	private final PersistentSessionPrincipalRestorer principalRestorer;

	private Duration maxInactiveInterval = Duration.ofMinutes(30);

	private Duration updateSessionIdAfter = Duration.ofMinutes(1);

	private Clock clock = Clock.systemUTC();

	public PersistentSessionRepository(SecretKey secretKey,
			PersistentSessionPrincipalNameResolver principalNameResolver,
			PersistentSessionPrincipalRestorer principalRestorer) {
		Assert.notNull(secretKey, "secretKey cannot be null");
		Assert.notNull(principalNameResolver, "principalNameResolver cannot be null");
		Assert.notNull(principalRestorer, "principalRestorer cannot be null");
		int keyLength = secretKey.getEncoded().length;
		boolean isExpectedLength = keyLength == 16 || keyLength == 24 || keyLength == 32;
		Assert.state(isExpectedLength, "key must be 16, 24 or 32 bytes in length");
		this.encryptor = new AesBytesEncryptor(secretKey, AesBytesEncryptor.CipherAlgorithm.GCM.defaultIvGenerator(),
				AesBytesEncryptor.CipherAlgorithm.GCM);
		this.principalNameResolver = principalNameResolver;
		this.principalRestorer = principalRestorer;
	}

	@Override
	public PersistentSession createSession() {
		return new PersistentSession();
	}

	@Override
	public void save(PersistentSession session) {
		String username = this.principalNameResolver.resolve(session);
		if (!StringUtils.hasText(username)) {
			session.setId("");
			return;
		}
		Instant lastSessionIdUpdate = session.getExpireAt().minus(this.maxInactiveInterval);
		boolean generateNewId = session.getId().isEmpty()
				|| lastSessionIdUpdate.plus(this.updateSessionIdAfter).isBefore(this.clock.instant());
		if (!generateNewId) {
			return;
		}
		String formatted = session.getExpireAt().toEpochMilli() + SEPARATOR + username;
		byte[] encrypted = this.encryptor.encrypt(formatted.getBytes(StandardCharsets.UTF_8));
		String encryptedId = new String(Hex.encode(encrypted));
		session.setId(encryptedId);
	}

	@Override
	public PersistentSession findById(String encryptedId) {
		byte[] decrypted = this.encryptor.decrypt(Hex.decode(encryptedId));
		String rawValue = new String(decrypted, StandardCharsets.UTF_8);
		StringTokenizer tokenizer = new StringTokenizer(rawValue, SEPARATOR);
		if (tokenizer.countTokens() != 2) {
			return null;
		}
		long expireAtMillis = Long.parseLong(tokenizer.nextToken());
		Instant expireAt = Instant.ofEpochMilli(expireAtMillis);
		if (expireAt.isBefore(this.clock.instant())) {
			return null;
		}
		String username = tokenizer.nextToken();
		PersistentSession persistentSession = new PersistentSession(encryptedId, expireAt);
		try {
			this.principalRestorer.restore(username, persistentSession);
		}
		catch (PrincipalRestoreException ex) {
			return null;
		}
		return persistentSession;
	}

	@Override
	public void deleteById(String id) {

	}

	/**
	 * The maximum time a session can stay inactive without expiring
	 * @param maxInactiveInterval the {@link Duration} representing the interval
	 */
	public void setMaxInactiveInterval(Duration maxInactiveInterval) {
		Assert.notNull(maxInactiveInterval, "maxInactiveInterval cannot be null");
		this.maxInactiveInterval = maxInactiveInterval;
	}

	/**
	 * The interval used to decide whether a new id should be generated. It is used to
	 * avoid writing a new id to the session every time the
	 * {@link #save(PersistentSession)} is called.
	 * @param updateSessionIdAfter the {@link Duration} used for the interval. Cannot be
	 * null.
	 */
	public void setUpdateSessionIdAfter(Duration updateSessionIdAfter) {
		Assert.notNull(updateSessionIdAfter, "newIdThreshold cannot be null");
		this.updateSessionIdAfter = updateSessionIdAfter;
	}

	/**
	 * The clock used to retrieve the current time.
	 * @param clock the {@link Clock}. Cannot be null.
	 */
	public void setClock(Clock clock) {
		Assert.notNull(clock, "clock cannot be null");
		this.clock = clock;
	}

	public final class PersistentSession implements Session {

		private String id = "";

		private final Map<String, Object> sessionAttrs = new HashMap<>();

		private Instant lastAccessTime;

		private Duration maxInactiveInterval;

		private Instant expireAt;

		PersistentSession() {
			this.lastAccessTime = PersistentSessionRepository.this.clock.instant();
			this.maxInactiveInterval = PersistentSessionRepository.this.maxInactiveInterval;
			this.expireAt = this.lastAccessTime.plusSeconds(this.maxInactiveInterval.getSeconds());
		}

		PersistentSession(String id, Instant expireAt) {
			this();
			this.id = id;
			this.expireAt = expireAt;
			this.lastAccessTime = expireAt.minus(this.maxInactiveInterval);
		}

		void setId(String id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return this.id;
		}

		@Override
		public String changeSessionId() {
			return this.id;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T getAttribute(String attributeName) {
			return (T) this.sessionAttrs.get(attributeName);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.sessionAttrs.keySet();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.sessionAttrs.put(attributeName, attributeValue);
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.sessionAttrs.remove(attributeName);
		}

		@Override
		public Instant getCreationTime() {
			return Instant.EPOCH;
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.lastAccessTime = lastAccessedTime;
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.lastAccessTime;
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.maxInactiveInterval = interval;
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.maxInactiveInterval;
		}

		@Override
		public boolean isExpired() {
			return this.expireAt.isBefore(PersistentSessionRepository.this.clock.instant());
		}

		private Instant getExpireAt() {
			return this.expireAt;
		}

		public void setExpireAt(Instant expireAt) {
			this.expireAt = expireAt;
		}

	}

}
