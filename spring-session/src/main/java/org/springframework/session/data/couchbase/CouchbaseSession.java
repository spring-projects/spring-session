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
package org.springframework.session.data.couchbase;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.util.Assert;

/**
 * An {@link ExpiringSession} that supports principals HTTP sessions.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
public class CouchbaseSession implements ExpiringSession, Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * HTTP session creation time attribute name.
	 */
	public static final String CREATION_TIME_ATTRIBUTE = "$creationTime";

	/**
	 * HTTP session last access time attribute name.
	 */
	public static final String LAST_ACCESSED_TIME_ATTRIBUTE = "$lastAccessedTime";

	/**
	 * HTTP session timeout attribute name.
	 */
	public static final String MAX_INACTIVE_INTERVAL_ATTRIBUTE = "$maxInactiveInterval";

	private static final Log log = LogFactory.getLog(CouchbaseSession.class);

	protected String id = UUID.randomUUID().toString();
	protected Map<String, Object> attributes = new HashMap<String, Object>();
	protected boolean principalSession = false;

	public CouchbaseSession(int timeoutInSeconds) {
		long now = System.currentTimeMillis();
		setCreationTime(now);
		setLastAccessedTime(now);
		setMaxInactiveIntervalInSeconds(timeoutInSeconds);
	}

	public CouchbaseSession(String id, Map<String, Object> attributes) {
		this.id = id;
		this.attributes = attributes == null ? new HashMap<String, Object>() : attributes;
		if (containsPrincipalAttribute()) {
			this.principalSession = true;
		}
	}

	public long getCreationTime() {
		return (Long) this.attributes.get(CREATION_TIME_ATTRIBUTE);
	}

	public long getLastAccessedTime() {
		return (Long) this.attributes.get(LAST_ACCESSED_TIME_ATTRIBUTE);
	}

	public void setLastAccessedTime(long lastAccessedTime) {
		this.attributes.put(LAST_ACCESSED_TIME_ATTRIBUTE, lastAccessedTime);
	}

	public void setMaxInactiveIntervalInSeconds(int interval) {
		this.attributes.put(MAX_INACTIVE_INTERVAL_ATTRIBUTE, interval);
	}

	public int getMaxInactiveIntervalInSeconds() {
		return (Integer) this.attributes.get(MAX_INACTIVE_INTERVAL_ATTRIBUTE);
	}

	public boolean isExpired() {
		return getMaxInactiveIntervalInSeconds() >= 0
				&& System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(
						getMaxInactiveIntervalInSeconds()) >= getLastAccessedTime();
	}

	public String getId() {
		return this.id;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String attributeName) {
		checkAttributeName(attributeName);
		T attribute = (T) this.attributes.get(attributeName);
		if (log.isTraceEnabled()) {
			log.trace("Read HTTP session attribute: [name='" + attributeName + "', value="
					+ attribute + "]");
		}
		return attribute;
	}

	public Set<String> getAttributeNames() {
		Set<String> attributesNames = this.attributes.keySet();
		return Collections.unmodifiableSet(attributesNames);
	}

	public void setAttribute(String attributeName, Object attributeValue) {
		checkAttributeName(attributeName);
		if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
				.equals(attributeName)) {
			this.principalSession = true;
		}
		this.attributes.put(attributeName, attributeValue);
		if (log.isTraceEnabled()) {
			log.trace("Set HTTP session attribute: [name='" + attributeName + "', value="
					+ attributeValue + "]");
		}
	}

	public void removeAttribute(String attributeName) {
		checkAttributeName(attributeName);
		this.attributes.remove(attributeName);
		if (log.isTraceEnabled()) {
			log.trace("Removed HTTP session attribute: [name='" + attributeName + "']");
		}
	}

	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

	public boolean isPrincipalSession() {
		return StringUtils.isNotBlank(getPrincipalAttribute());
	}

	public String getPrincipalAttribute() {
		Object principal = this.attributes
				.get(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		return (String) principal;
	}

	protected void checkAttributeName(String attributeName) {
		Assert.hasText(attributeName, "Empty HTTP session attribute name");
	}

	protected void setCreationTime(long creationTime) {
		this.attributes.put(CREATION_TIME_ATTRIBUTE, creationTime);
	}

	protected boolean containsPrincipalAttribute() {
		return this.attributes
				.containsKey(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
	}
}
