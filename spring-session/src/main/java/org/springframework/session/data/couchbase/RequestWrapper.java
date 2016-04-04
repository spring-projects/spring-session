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

import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.Assert;

/**
 * A {@link HttpServletRequestWrapper} that adds support for changing HTTP session ID when
 * session contains namespaces.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 * @see CouchbaseSession
 */
public class RequestWrapper extends HttpServletRequestWrapper {

	private static final Logger log = LoggerFactory.getLogger(RequestWrapper.class);

	protected final CouchbaseDao dao;
	protected final String namespace;
	protected final Serializer serializer;

	public RequestWrapper(HttpServletRequest request, CouchbaseDao dao, String namespace,
			Serializer serializer) {
		super(request);
		this.dao = dao;
		this.namespace = namespace;
		this.serializer = serializer;
	}

	public String changeSessionId() {
		SessionDocument oldDocument = dao.findById(getRequestedSessionId());
		Assert.notNull(oldDocument,
				"Cannot change HTTP session ID, because session with ID '"
						+ getRequestedSessionId() + "' does not exist");

		removeAttribute(SessionRepositoryFilter.CURRENT_SESSION_ATTR);
		dao.delete(oldDocument.getId());

		HttpSession newSession = getSession();
		SessionDocument newDocument = new SessionDocument(newSession.getId(),
				oldDocument.getData());
		dao.save(newDocument);

		copyGlobalAttributes(oldDocument, newSession);
		copyNamespaceAttributes(oldDocument, newSession);

		log.debug("HTTP session ID changed from {} to {}", oldDocument.getId(),
				newDocument.getId());

		return newDocument.getId();
	}

	protected void copyGlobalAttributes(SessionDocument oldDocument,
			HttpSession newSession) {
		Map<String, Object> attributes = oldDocument.getData()
				.get(CouchbaseSessionRepository.GLOBAL_NAMESPACE);
		if (attributes != null) {
			Map<String, Object> deserializedAttributes = serializer
					.deserializeSessionAttributes(attributes);
			for (Entry<String, Object> attribute : deserializedAttributes.entrySet()) {
				newSession.setAttribute(
						CouchbaseSession.globalAttributeName(attribute.getKey()),
						attribute.getValue());
			}
		}
	}

	protected void copyNamespaceAttributes(SessionDocument oldDocument,
			HttpSession newSession) {
		Map<String, Object> attributes = oldDocument.getData().get(namespace);
		if (attributes != null) {
			Map<String, Object> deserializedAttributes = serializer
					.deserializeSessionAttributes(attributes);
			for (Entry<String, Object> attribute : deserializedAttributes.entrySet()) {
				newSession.setAttribute(attribute.getKey(), attribute.getValue());
			}
		}
	}
}
