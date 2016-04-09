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

package org.springframework.session.security;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionInformation;

import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * Ensures that calling {@link #expireNow()} propagates to Spring Session,
 * since this session information contains only derived data and is not the authoritative source.
 *
 * @author Joris Kuipers
 * @since 1.2
 */
class SpringSessionBackedSessionInformation extends SessionInformation {

	private static final Log logger = LogFactory.getLog(SpringSessionBackedSessionInformation.class);

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	/**
	 * Tries to determine the principal's name from the given Session.
	 *
	 * @param session Spring Session session
	 * @return the principal's name, or empty String if it couldn't be determined
	 */
	private static String resolvePrincipal(Session session) {
		String principalName = session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		if (principalName != null) {
			return principalName;
		}
		SecurityContext securityContext = session.getAttribute(SPRING_SECURITY_CONTEXT);
		if (securityContext != null && securityContext.getAuthentication() != null) {
			return securityContext.getAuthentication().getName();
		}
		return "";
	}

	private final SessionRepository<? extends ExpiringSession> sessionRepository;

	SpringSessionBackedSessionInformation(ExpiringSession session, SessionRepository<? extends ExpiringSession> sessionRepository) {
		super(resolvePrincipal(session), session.getId(), new Date(session.getLastAccessedTime()));
		this.sessionRepository = sessionRepository;
		if (session.isExpired()) {
			super.expireNow();
		}
	}

	@Override
	public void expireNow() {
		if (logger.isDebugEnabled()) {
			logger.debug("Deleting session " + getSessionId() + " for user '" + getPrincipal() +
					"', presumably because maximum allowed concurrent sessions was exceeded");
		}
		super.expireNow();
		this.sessionRepository.delete(getSessionId());
	}

}
