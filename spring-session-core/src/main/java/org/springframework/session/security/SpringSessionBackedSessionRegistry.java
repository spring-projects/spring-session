/*
 * Copyright 2014-2018 the original author or authors.
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

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

/**
 * A {@link SessionRegistry} that retrieves session information from Spring Session,
 * rather than maintaining it itself. This allows concurrent session management with
 * Spring Security in a clustered environment.
 * <p>
 * Relies on being able to derive the same String-based representation of the principal
 * given to {@link #getAllSessions(Object, boolean)} as used by Spring Session in order to
 * look up the user's sessions.
 * <p>
 * Does not support {@link #getAllPrincipals()}, since that information is not available.
 *
 * @param <S> the {@link Session} type.
 * @author Joris Kuipers
 * @author Vedran Pavic
 * @since 1.3
 */
public class SpringSessionBackedSessionRegistry<S extends Session>
		implements SessionRegistry {

	private final FindByIndexNameSessionRepository<S> sessionRepository;

	public SpringSessionBackedSessionRegistry(
			FindByIndexNameSessionRepository<S> sessionRepository) {
		Assert.notNull(sessionRepository, "sessionRepository cannot be null");
		this.sessionRepository = sessionRepository;
	}

	@Override
	public List<Object> getAllPrincipals() {
		throw new UnsupportedOperationException("SpringSessionBackedSessionRegistry does "
				+ "not support retrieving all principals, since Spring Session provides "
				+ "no way to obtain that information");
	}

	@Override
	public List<SessionInformation> getAllSessions(Object principal,
			boolean includeExpiredSessions) {
		Collection<S> sessions = this.sessionRepository
				.findByPrincipalName(name(principal)).values();
		List<SessionInformation> infos = new ArrayList<>();
		for (S session : sessions) {
			if (includeExpiredSessions || !Boolean.TRUE.equals(session
					.getAttribute(SpringSessionBackedSessionInformation.EXPIRED_ATTR))) {
				infos.add(new SpringSessionBackedSessionInformation<>(session,
						this.sessionRepository));
			}
		}
		return infos;
	}

	@Override
	public SessionInformation getSessionInformation(String sessionId) {
		S session = this.sessionRepository.findById(sessionId);
		if (session != null) {
			return new SpringSessionBackedSessionInformation<>(session,
					this.sessionRepository);
		}
		return null;
	}

	/*
	 * This is a no-op, as we don't administer sessions ourselves.
	 */
	@Override
	public void refreshLastRequest(String sessionId) {
	}

	/*
	 * This is a no-op, as we don't administer sessions ourselves.
	 */
	@Override
	public void registerNewSession(String sessionId, Object principal) {
	}

	/*
	 * This is a no-op, as we don't administer sessions ourselves.
	 */
	@Override
	public void removeSessionInformation(String sessionId) {
	}

	/**
	 * Derives a String name for the given principal.
	 *
	 * @param principal as provided by Spring Security
	 * @return name of the principal, or its {@code toString()} representation if no name
	 * could be derived
	 */
	protected String name(Object principal) {
		if (principal instanceof UserDetails) {
			return ((UserDetails) principal).getUsername();
		}
		if (principal instanceof Principal) {
			return ((Principal) principal).getName();
		}
		return principal.toString();
	}

}
