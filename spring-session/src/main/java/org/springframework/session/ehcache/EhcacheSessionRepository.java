/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.ehcache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.search.Query;
import net.sf.ehcache.search.Result;
import net.sf.ehcache.search.Results;
import net.sf.ehcache.search.attribute.DynamicAttributesExtractor;
import net.sf.ehcache.search.expression.EqualTo;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

/**
 * A {@link org.springframework.session.SessionRepository} implementation that uses
 * Spring's {@link net.sf.ehcache.Ehcache} to store sessions in a in-memory cache. This
 * implementation supports publishing of session events by default.
 *
 * @author Jan Pichanic
 * @since 1.2.0
 */
public final class EhcacheSessionRepository implements FindByIndexNameSessionRepository<EhcacheSessionRepository.EhcacheSession> {

	private static PrincipalNameResolver principalNameResolver = new PrincipalNameResolver();
	private int maxInactiveIntervalInSeconds;
	private final Ehcache cache;

	public EhcacheSessionRepository(Ehcache cache) {
		Assert.notNull(cache, "Cache cannot be null");
		this.cache = cache;
		this.cache.registerDynamicAttributesExtractor(new DynamicAttributesExtractor() {
			@SuppressWarnings("unchecked")
			@Override
			public Map<String, Object> attributesFor(Element element) {
				final Map<String, Object> attributes = new HashMap<String, Object>(1);
				final EhcacheSession session = (EhcacheSession) element.getObjectValue();
				// Put NULL value as default - need because of dynamic attribute extractor.
				// If no PRINCIPAL_NAME_INDEX_NAME is present for every element, then exception is thrown
				attributes.put(PRINCIPAL_NAME_INDEX_NAME, null);

				for (String key : session.getAttributeNames()) {
					if (key.equals(PRINCIPAL_NAME_INDEX_NAME)) {
						attributes.put(PRINCIPAL_NAME_INDEX_NAME, session.getAttribute(key));
					}
				}
				return attributes;
			}
		});
	}

	/**
	 * Set maximum inactive interval for sessions.
	 *
	 * @param maxInactiveIntervalInSeconds the session maximum inactive interval in seconds
	 */
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		Assert.isTrue(maxInactiveIntervalInSeconds > 0, "Max inactive interval in seconds must be positive number");
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	/**
	 * Sets the {@link ApplicationEventPublisher} that is used to publish
	 * session events.
	 *
	 * @param applicationEventPublisher the {@link ApplicationEventPublisher} that is used
	 *                                  to publish {@link SessionDestroyedEvent}. Cannot be null.
	 */
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher, "applicationEventPublisher cannot be null");
		EhcacheEventListener ehcacheEventListener = new EhcacheEventListener(applicationEventPublisher);
		this.cache.getCacheEventNotificationService().registerListener(ehcacheEventListener);
	}

	@Override
	public EhcacheSession createSession() {
		EhcacheSession session = new EhcacheSession();
		session.setMaxInactiveIntervalInSeconds(this.maxInactiveIntervalInSeconds);
		return session;
	}

	@Override
	public void save(EhcacheSession session) {
		session.resolvePrincipalName();
		Element element = new Element(session.getId(), session);
		element.setTimeToIdle(session.getMaxInactiveIntervalInSeconds());
		this.cache.put(element);
	}

	@Override
	public EhcacheSession getSession(String id) {
		Element element = this.cache.get(id);
		if (element == null || element.isExpired()) {
			return null;
		}
		EhcacheSession session = (EhcacheSession) element.getObjectValue();
		session.setLastAccessedTime(element.getLastAccessTime());
		return (EhcacheSession) element.getObjectValue();
	}

	@Override
	public void delete(String id) {
		Element element = this.cache.get(id);
		if (element == null || element.isExpired()) {
			return;
		}
		this.cache.remove(id);
	}

	@Override
	public Map<String, EhcacheSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		if (!indexName.equals(PRINCIPAL_NAME_INDEX_NAME) || indexValue == null || indexValue.isEmpty()) {
			return Collections.emptyMap();
		}

		HashMap<String, EhcacheSession> result = new HashMap<String, EhcacheSession>(1);
		Query query = this.cache.createQuery()
				.includeKeys()
				.includeValues()
				.addCriteria(new EqualTo(PRINCIPAL_NAME_INDEX_NAME, indexValue))
				.end();
		Results queryResult = query.execute();
		for (Result r : queryResult.all()) {
			EhcacheSession ehcacheSession = (EhcacheSession) r.getValue();
			if (!ehcacheSession.isExpired()) {
				result.put((String) r.getKey(), ehcacheSession);
			}
		}
		return result;
	}

	/**
	 * Implementation of {@link net.sf.ehcache.event.CacheEventListener}.
	 */
	private final class EhcacheEventListener implements CacheEventListener {

		private ApplicationEventPublisher eventPublisher;

		EhcacheEventListener(ApplicationEventPublisher eventPublisher) {
			this.eventPublisher = eventPublisher;
		}

		@Override
		public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
			this.eventPublisher.publishEvent(new SessionDeletedEvent(this, (Session) element.getObjectValue()));
		}

		@Override
		public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
			this.eventPublisher.publishEvent(new SessionCreatedEvent(this, (Session) element.getObjectValue()));
		}

		@Override
		public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {
		}

		@Override
		public void notifyElementExpired(Ehcache cache, Element element) {
			this.eventPublisher.publishEvent(new SessionExpiredEvent(this, (Session) element.getObjectValue()));
		}

		@Override
		public void notifyElementEvicted(Ehcache cache, Element element) {

		}

		@Override
		public void notifyRemoveAll(Ehcache cache) {

		}

		@Override
		public void dispose() {

		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}

	/**
	 * Ehcache session.
	 *
	 * @see ExpiringSession
	 */
	public final class EhcacheSession implements ExpiringSession {

		private MapSession cached;

		public EhcacheSession(MapSession cached) {
			this.cached = cached;
		}

		public EhcacheSession() {
			this(new MapSession());
		}

		public void resolvePrincipalName() {
			String principalName = getPrincipalName();
			setAttribute(PRINCIPAL_NAME_INDEX_NAME, principalName);
		}

		public String getPrincipalName() {
			return principalNameResolver.resolvePrincipal(this);
		}

		@Override
		public String getId() {
			return this.cached.getId();
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getAttribute(String attributeName) {
			return (T) this.cached.getAttribute(attributeName);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.cached.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.cached.setAttribute(attributeName, attributeValue);
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.cached.removeAttribute(attributeName);
		}

		@Override
		public long getCreationTime() {
			return this.cached.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(long lastAccessedTime) {
			this.cached.setLastAccessedTime(lastAccessedTime);
		}

		@Override
		public long getLastAccessedTime() {
			return this.cached.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveIntervalInSeconds(int interval) {
			this.cached.setMaxInactiveIntervalInSeconds(interval);
		}

		@Override
		public int getMaxInactiveIntervalInSeconds() {
			return this.cached.getMaxInactiveIntervalInSeconds();
		}

		@Override
		public boolean isExpired() {
			return this.cached.isExpired();
		}

	}

	/**
	 * Principal name resolver helper class.
	 */
	static class PrincipalNameResolver {

		private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

		private SpelExpressionParser parser = new SpelExpressionParser();

		public String resolvePrincipal(Session session) {

			Object authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (authentication != null) {
				Expression expression = this.parser
						.parseExpression("authentication?.name");
				return expression.getValue(authentication, String.class);
			}

			String principalName = session.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
			if (principalName != null) {
				return principalName;
			}
			return null;
		}
	}
}
