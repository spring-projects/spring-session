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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.util.Assert;

/**
 * An {@link ExpiringSession} that supports HTTP session namespaces. Namespaces are
 * divided into two groups: <br>
 * <br>
 * <b>Global namespace:</b> session attributes are visible to all instances of all web
 * applications within a distributed system. To set or get global attribute value convert
 * its name using {@link CouchbaseSession#globalAttributeName(String)} <br>
 * <br>
 * <b>Application namespace:</b> attributes are visible only to instances of the same web
 * application within a distributed system. To set or get global attribute value just use
 * its name as it is.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
public class CouchbaseSession implements ExpiringSession, Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * HTTP session creation time attribute name. To get the creation time convert it to
	 * global attribute name using {@link CouchbaseSession#globalAttributeName(String)}.
	 */
	public static final String CREATION_TIME_ATTRIBUTE = "$creationTime";

	/**
	 * HTTP session last access time attribute name. To get the last access time convert
	 * it to global attribute name using
	 * {@link CouchbaseSession#globalAttributeName(String)}.
	 */
	public static final String LAST_ACCESSED_TIME_ATTRIBUTE = "$lastAccessedTime";

	/**
	 * HTTP session timeout attribute name. To get the timeout in seconds convert it to
	 * global attribute name using {@link CouchbaseSession#globalAttributeName(String)}.
	 */
	public static final String MAX_INACTIVE_INTERVAL_ATTRIBUTE = "$maxInactiveInterval";
	protected static final String GLOBAL_ATTRIBUTE_NAME_PREFIX = CouchbaseSession.class
			.getName() + ".global.";

	private static final Logger log = LoggerFactory.getLogger(CouchbaseSession.class);

	protected String id = UUID.randomUUID().toString();
	protected Map<String, Object> globalAttributes = new HashMap<String, Object>();
	protected Map<String, Object> namespaceAttributes = new HashMap<String, Object>();
	protected boolean namespacePersistenceRequired = false;
	protected boolean principalSession = false;

	public CouchbaseSession(int timeoutInSeconds) {
		long now = System.currentTimeMillis();
		setCreationTime(now);
		setLastAccessedTime(now);
		setMaxInactiveIntervalInSeconds(timeoutInSeconds);
	}

	public CouchbaseSession(String id, Map<String, Object> globalAttributes,
			Map<String, Object> namespaceAttributes) {
		this.id = id;
		this.globalAttributes = globalAttributes == null ? new HashMap<String, Object>()
				: globalAttributes;
		this.namespaceAttributes = namespaceAttributes == null
				? new HashMap<String, Object>() : namespaceAttributes;
		if (containsPrincipalAttribute()) {
			principalSession = true;
		}
	}

	/**
	 * Converts HTTP session attribute name to global attribute name. The conversion is
	 * necessary for setting and getting global HTTP session attributes values.
	 *
	 * @param attributeName attribute name to convert
	 * @return global attribute name
	 * @see CouchbaseSession
	 */
	public static String globalAttributeName(String attributeName) {
		return GLOBAL_ATTRIBUTE_NAME_PREFIX + attributeName;
	}

	public long getCreationTime() {
		return Math.round((Double) globalAttributes.get(CREATION_TIME_ATTRIBUTE));
	}

	public long getLastAccessedTime() {
		return Math.round((Double) globalAttributes.get(LAST_ACCESSED_TIME_ATTRIBUTE));
	}

	public void setLastAccessedTime(long lastAccessedTime) {
		globalAttributes.put(LAST_ACCESSED_TIME_ATTRIBUTE, lastAccessedTime);
	}

	public void setMaxInactiveIntervalInSeconds(int interval) {
		globalAttributes.put(MAX_INACTIVE_INTERVAL_ATTRIBUTE, interval);
	}

	public int getMaxInactiveIntervalInSeconds() {
		return (Integer) globalAttributes.get(MAX_INACTIVE_INTERVAL_ATTRIBUTE);
	}

	public boolean isExpired() {
		return getMaxInactiveIntervalInSeconds() >= 0
				&& System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(
						getMaxInactiveIntervalInSeconds()) >= getLastAccessedTime();
	}

	public String getId() {
		return id;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String attributeName) {
		checkAttributeName(attributeName);
		T attribute;
		if (isGlobal(attributeName)) {
			String name = getNameFromGlobalName(attributeName);
			attribute = (T) globalAttributes.get(name);
			log.trace("Read global HTTP session attribute: [name='{}', value={}]", name,
					attribute);
		}
		else {
			attribute = (T) namespaceAttributes.get(attributeName);
			log.trace(
					"Read application namespace HTTP session attribute: [name='{}', value={}]",
					attributeName, attribute);
		}
		return attribute;
	}

	public Set<String> getAttributeNames() {
		Set<String> attributesNames = new HashSet<String>();
		for (String attributeName : globalAttributes.keySet()) {
			attributesNames.add(globalAttributeName(attributeName));
		}
		attributesNames.addAll(namespaceAttributes.keySet());
		return Collections.unmodifiableSet(attributesNames);
	}

	public void setAttribute(String attributeName, Object attributeValue) {
		checkAttributeName(attributeName);
		if (isGlobal(attributeName)) {
			String name = getNameFromGlobalName(attributeName);
			if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME.equals(name)) {
				principalSession = true;
			}
			globalAttributes.put(name, attributeValue);
			log.trace("Set global HTTP session attribute: [name='{}', value={}]", name,
					attributeValue);
		}
		else {
			if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
					.equals(attributeName)) {
				principalSession = true;
			}
			namespacePersistenceRequired = true;
			namespaceAttributes.put(attributeName, attributeValue);
			log.trace(
					"Set application namespace HTTP session attribute: [name='{}', value={}]",
					attributeName, attributeValue);
		}
	}

	public void removeAttribute(String attributeName) {
		checkAttributeName(attributeName);
		if (isGlobal(attributeName)) {
			String name = getNameFromGlobalName(attributeName);
			globalAttributes.remove(name);
			log.trace("Removed global HTTP session attribute: [name='{}']", name);
		}
		else {
			namespacePersistenceRequired = true;
			namespaceAttributes.remove(attributeName);
			log.trace("Removed application namespace HTTP session attribute: [name='{}']",
					attributeName);
		}
	}

	public Map<String, Object> getGlobalAttributes() {
		return globalAttributes;
	}

	public Map<String, Object> getNamespaceAttributes() {
		return namespaceAttributes;
	}

	public boolean isNamespacePersistenceRequired() {
		return namespacePersistenceRequired;
	}

	public boolean isPrincipalSession() {
		return StringUtils.isNotBlank(getPrincipalAttribute());
	}

	public String getPrincipalAttribute() {
		Object principal = globalAttributes
				.get(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		if (principal == null) {
			principal = namespaceAttributes
					.get(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		}
		return (String) principal;
	}

	protected void setCreationTime(long creationTime) {
		globalAttributes.put(CREATION_TIME_ATTRIBUTE, creationTime);
	}

	protected void checkAttributeName(String attributeName) {
		Assert.hasText(attributeName, "Empty HTTP session attribute name");
		Assert.isTrue(!attributeName.trim().equals(GLOBAL_ATTRIBUTE_NAME_PREFIX),
				"Empty HTTP session global attribute name");
	}

	protected boolean isGlobal(String attributeName) {
		return attributeName.startsWith(GLOBAL_ATTRIBUTE_NAME_PREFIX);
	}

	protected String getNameFromGlobalName(String globalAttributeName) {
		return StringUtils.removeStart(globalAttributeName, GLOBAL_ATTRIBUTE_NAME_PREFIX);
	}

	protected boolean containsPrincipalAttribute() {
		return globalAttributes
				.containsKey(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)
				|| namespaceAttributes.containsKey(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
	}
}
