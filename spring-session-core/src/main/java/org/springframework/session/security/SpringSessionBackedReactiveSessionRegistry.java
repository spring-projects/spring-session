/*
 * Copyright 2014-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.security;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.ReactiveSessionInformation;
import org.springframework.security.core.session.ReactiveSessionRegistry;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

/**
 * A {@link ReactiveSessionRegistry} that retrieves session information from Spring
 * Session, rather than maintaining it itself. This allows concurrent session management
 * with Spring Security in a clustered environment.
 * <p>
 * Relies on being able to derive the same String-based representation of the principal
 * given to {@link #getAllSessions(Object)} as used by Spring Session in order to look up
 * the user's sessions.
 * <p>
 *
 * @param <S> the {@link Session} type.
 * @author Marcus da Coregio
 * @since 3.3
 */
public final class SpringSessionBackedReactiveSessionRegistry<S extends Session> implements ReactiveSessionRegistry {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private final ReactiveSessionRepository<S> sessionRepository;

	private final ReactiveFindByIndexNameSessionRepository<S> indexedSessionRepository;

	public SpringSessionBackedReactiveSessionRegistry(ReactiveSessionRepository<S> sessionRepository,
			ReactiveFindByIndexNameSessionRepository<S> indexedSessionRepository) {
		Assert.notNull(sessionRepository, "sessionRepository cannot be null");
		Assert.notNull(indexedSessionRepository, "indexedSessionRepository cannot be null");
		this.sessionRepository = sessionRepository;
		this.indexedSessionRepository = indexedSessionRepository;
	}

	@Override
	public Flux<ReactiveSessionInformation> getAllSessions(Object principal) {
		Authentication authenticationToken = getAuthenticationToken(principal);
		return this.indexedSessionRepository.findByPrincipalName(authenticationToken.getName())
			.flatMapMany((sessionMap) -> Flux.fromIterable(sessionMap.entrySet()))
			.map((entry) -> new SpringSessionBackedReactiveSessionInformation(entry.getValue()));
	}

	@Override
	public Mono<Void> saveSessionInformation(ReactiveSessionInformation information) {
		return Mono.empty();
	}

	@Override
	public Mono<ReactiveSessionInformation> getSessionInformation(String sessionId) {
		return this.sessionRepository.findById(sessionId).map(SpringSessionBackedReactiveSessionInformation::new);
	}

	@Override
	public Mono<ReactiveSessionInformation> removeSessionInformation(String sessionId) {
		return Mono.empty();
	}

	@Override
	public Mono<ReactiveSessionInformation> updateLastAccessTime(String sessionId) {
		return Mono.empty();
	}

	private static Authentication getAuthenticationToken(Object principal) {
		return new AbstractAuthenticationToken(AuthorityUtils.NO_AUTHORITIES) {

			@Override
			public Object getCredentials() {
				return null;
			}

			@Override
			public Object getPrincipal() {
				return principal;
			}

		};
	}

	class SpringSessionBackedReactiveSessionInformation extends ReactiveSessionInformation {

		SpringSessionBackedReactiveSessionInformation(S session) {
			super(resolvePrincipalName(session), session.getId(), session.getLastAccessedTime());
		}

		private static String resolvePrincipalName(Session session) {
			String principalName = session
				.getAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
			if (principalName != null) {
				return principalName;
			}
			SecurityContext securityContext = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (securityContext != null && securityContext.getAuthentication() != null) {
				return securityContext.getAuthentication().getName();
			}
			return "";
		}

		@Override
		public Mono<Void> invalidate() {
			return super.invalidate()
				.then(Mono.defer(() -> SpringSessionBackedReactiveSessionRegistry.this.sessionRepository
					.deleteById(getSessionId())));
		}

	}

}
