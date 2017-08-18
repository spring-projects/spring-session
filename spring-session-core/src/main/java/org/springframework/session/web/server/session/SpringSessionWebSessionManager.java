/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.web.server.session;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.session.ReactorSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;
import org.springframework.web.server.session.WebSessionManager;
import org.springframework.web.server.session.WebSessionStore;

/**
 * The {@link WebSessionManager} implementation backed by
 * {@link ReactorSessionRepository}.
 *
 * @author Rob Winch
 * @since 2.0
 */
public class SpringSessionWebSessionManager implements WebSessionManager {

	private final SpringSessionWebSessionStore<? extends Session> sessionStore;

	private WebSessionIdResolver sessionIdResolver = new CookieWebSessionIdResolver();

	private Clock clock = Clock.system(ZoneOffset.UTC);

	public SpringSessionWebSessionManager(
			ReactorSessionRepository<? extends Session> sessionRepository) {
		this.sessionStore = new SpringSessionWebSessionStore<>(sessionRepository);
	}

	/**
	 * Return the configured {@link WebSessionIdResolver}.
	 * @return the configured {@link WebSessionIdResolver}
	 */
	private WebSessionIdResolver getSessionIdResolver() {
		return this.sessionIdResolver;
	}

	/**
	 * Configure the id resolution strategy.
	 * <p>
	 * By default an instance of {@link CookieWebSessionIdResolver}.
	 * @param sessionIdResolver the resolver to use
	 */
	public void setSessionIdResolver(WebSessionIdResolver sessionIdResolver) {
		Assert.notNull(sessionIdResolver, "WebSessionIdResolver is required.");
		this.sessionIdResolver = sessionIdResolver;
	}

	/**
	 * Return the configured {@link WebSessionStore}.
	 * @return the configured {@link WebSessionStore}
	 */
	private WebSessionStore getSessionStore() {
		return this.sessionStore;
	}

	/**
	 * Return the configured clock for session {@code lastAccessTime} calculations.
	 * @return the configured clock for session {@code lastAccessTime} calculations
	 */
	private Clock getClock() {
		return this.clock;
	}

	/**
	 * Configure the {@link Clock} to use to set lastAccessTime on every created session
	 * and to calculate if it is expired.
	 * <p>
	 * This may be useful to align to different timezone or to set the clock back in a
	 * test, e.g. {@code Clock.offset(clock, Duration.ofMinutes(-31))} in order to
	 * simulate session expiration.
	 * <p>
	 * By default this is {@code Clock.system(ZoneOffset.UTC)}.
	 * @param clock the clock to use
	 */
	public void setClock(Clock clock) {
		Assert.notNull(clock, "'clock' is required.");
		this.clock = clock;
	}

	@Override
	public Mono<WebSession> getSession(ServerWebExchange exchange) {
		// @formatter:off
		return Mono.defer(() ->
				retrieveSession(exchange))
						.flatMap(session -> removeSessionIfExpired(exchange, session))
						.flatMap(session -> {
							Instant lastAccessTime = Instant.now(getClock());
							return this.sessionStore.setLastAccessedTime(session, lastAccessTime);
						})
						.switchIfEmpty(createSession(exchange))
						.doOnNext(session -> exchange.getResponse().beforeCommit(session::save));
		// @formatter:on
	}

	private Mono<WebSession> retrieveSession(ServerWebExchange exchange) {
		// @formatter:off
		return Flux.fromIterable(getSessionIdResolver().resolveSessionIds(exchange))
				.concatMap(sessionId -> this.sessionStore.retrieveSession(sessionId, session -> saveSession(exchange, session)))
				.cast(WebSession.class)
				.next();
		// @formatter:on
	}

	private Mono<WebSession> removeSessionIfExpired(ServerWebExchange exchange,
			WebSession session) {
		if (session.isExpired()) {
			this.sessionIdResolver.expireSession(exchange);
			return this.sessionStore.removeSession(session.getId()).then(Mono.empty());
		}
		return Mono.just(session);
	}

	private Mono<Void> saveSession(ServerWebExchange exchange, WebSession session) {
		if (session.isExpired()) {
			return Mono.error(new IllegalStateException(
					"Sessions are checked for expiration and have their "
							+ "lastAccessTime updated when first accessed during request processing. "
							+ "However this session is expired meaning that maxIdleTime elapsed "
							+ "before the call to session.save()."));
		}

		if (!session.isStarted()) {
			return Mono.empty();
		}

		// Force explicit start
		session.start();

		if (hasNewSessionId(exchange, session)) {
			this.sessionIdResolver.setSessionId(exchange, session.getId());
		}

		return this.sessionStore.storeSession(session);
	}

	private boolean hasNewSessionId(ServerWebExchange exchange, WebSession session) {
		List<String> ids = getSessionIdResolver().resolveSessionIds(exchange);
		return ids.isEmpty() || !session.getId().equals(ids.get(0));
	}

	private Mono<WebSession> createSession(ServerWebExchange exchange) {
		return this.sessionStore.createSession(session -> saveSession(exchange, session));
	}

}
