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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.session.Session;
import org.springframework.session.web.http.CookieHttpSessionStrategy;
import org.springframework.session.web.http.MultiHttpSessionStrategy;

/**
 * A {@link MultiHttpSessionStrategy} that wraps the original HTTP request with
 * {@link RequestWrapper}.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
public class DelegatingSessionStrategy implements MultiHttpSessionStrategy {

	protected final CookieHttpSessionStrategy sessionStrategy;
	protected final CouchbaseDao dao;
	protected final String namespace;
	protected final Serializer serializer;

	public DelegatingSessionStrategy(CookieHttpSessionStrategy sessionStrategy,
			CouchbaseDao dao, String namespace, Serializer serializer) {
		this.sessionStrategy = sessionStrategy;
		this.dao = dao;
		this.namespace = namespace;
		this.serializer = serializer;
	}

	public String getRequestedSessionId(HttpServletRequest request) {
		return this.sessionStrategy.getRequestedSessionId(request);
	}

	public void onNewSession(Session session, HttpServletRequest request,
			HttpServletResponse response) {
		this.sessionStrategy.onNewSession(session, request, response);
	}

	public void onInvalidateSession(HttpServletRequest request,
			HttpServletResponse response) {
		this.sessionStrategy.onInvalidateSession(request, response);
	}

	public HttpServletRequest wrapRequest(HttpServletRequest request,
			HttpServletResponse response) {
		RequestWrapper wrapper = new RequestWrapper(request, this.dao, this.namespace,
				this.serializer);
		return this.sessionStrategy.wrapRequest(wrapper, response);
	}

	public HttpServletResponse wrapResponse(HttpServletRequest request,
			HttpServletResponse response) {
		return this.sessionStrategy.wrapResponse(request, response);
	}
}
