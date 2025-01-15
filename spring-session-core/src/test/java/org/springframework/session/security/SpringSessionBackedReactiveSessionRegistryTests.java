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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.session.ReactiveSessionInformation;
import org.springframework.session.MapSession;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveMapSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSessionBackedReactiveSessionRegistryTests {

	static MapSession johnSession1 = new MapSession();
	static MapSession johnSession2 = new MapSession();
	static MapSession johnSession3 = new MapSession();

	SpringSessionBackedReactiveSessionRegistry<MapSession> sessionRegistry;

	ReactiveFindByIndexNameSessionRepository<MapSession> indexedSessionRepository = new StubIndexedSessionRepository();

	ReactiveMapSessionRepository sessionRepository = new ReactiveMapSessionRepository(new ConcurrentHashMap<>());

	static {
		johnSession1.setAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "johndoe");
		johnSession2.setAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "johndoe");
		johnSession3.setAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "johndoe");
	}

	@BeforeEach
	void setup() {
		this.sessionRegistry = new SpringSessionBackedReactiveSessionRegistry<>(this.sessionRepository,
				this.indexedSessionRepository);
		this.sessionRepository.save(johnSession1).block();
		this.sessionRepository.save(johnSession2).block();
		this.sessionRepository.save(johnSession3).block();
	}

	@Test
	void saveSessionInformationThenDoNothing() {
		StepVerifier.create(this.sessionRegistry.saveSessionInformation(null)).expectComplete().verify();
	}

	@Test
	void removeSessionInformationThenDoNothing() {
		StepVerifier.create(this.sessionRegistry.removeSessionInformation(null)).expectComplete().verify();
	}

	@Test
	void updateLastAccessTimeThenDoNothing() {
		StepVerifier.create(this.sessionRegistry.updateLastAccessTime(null)).expectComplete().verify();
	}

	@Test
	void getSessionInformationWhenPrincipalIndexNamePresentThenPrincipalResolved() {
		MapSession session = this.sessionRepository.createSession().block();
		session.setAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "johndoe");
		this.sessionRepository.save(session).block();
		StepVerifier.create(this.sessionRegistry.getSessionInformation(session.getId()))
			.assertNext((sessionInformation) -> {
				assertThat(sessionInformation.getSessionId()).isEqualTo(session.getId());
				assertThat(sessionInformation.getLastAccessTime()).isEqualTo(session.getLastAccessedTime());
				assertThat(sessionInformation.getPrincipal()).isEqualTo("johndoe");
			})
			.verifyComplete();
	}

	@Test
	void getSessionInformationWhenSecurityContextAttributePresentThenPrincipalResolved() {
		MapSession session = this.sessionRepository.createSession().block();
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("johndoe", "n/a");
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(authentication);
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		this.sessionRepository.save(session).block();
		StepVerifier.create(this.sessionRegistry.getSessionInformation(session.getId()))
			.assertNext((sessionInformation) -> {
				assertThat(sessionInformation.getSessionId()).isEqualTo(session.getId());
				assertThat(sessionInformation.getLastAccessTime()).isEqualTo(session.getLastAccessedTime());
				assertThat(sessionInformation.getPrincipal()).isEqualTo("johndoe");
			})
			.verifyComplete();
	}

	@Test
	void getSessionInformationWhenNoResolvablePrincipalThenPrincipalBlank() {
		MapSession session = this.sessionRepository.createSession().block();
		this.sessionRepository.save(session).block();
		StepVerifier.create(this.sessionRegistry.getSessionInformation(session.getId()))
			.assertNext((sessionInformation) -> {
				assertThat(sessionInformation.getSessionId()).isEqualTo(session.getId());
				assertThat(sessionInformation.getLastAccessTime()).isEqualTo(session.getLastAccessedTime());
				assertThat(sessionInformation.getPrincipal()).isEqualTo("");
			})
			.verifyComplete();
	}

	@Test
	void getSessionInformationWhenInvalidateThenRemovedFromSessionRepository() {
		MapSession session = this.sessionRepository.createSession().block();
		this.sessionRepository.save(session).block();
		Mono<Void> publisher = this.sessionRegistry.getSessionInformation(session.getId())
			.flatMap(ReactiveSessionInformation::invalidate);
		StepVerifier.create(publisher).verifyComplete();
		StepVerifier.create(this.sessionRepository.findById(session.getId())).expectComplete().verify();
	}

	@Test
	void getAllSessionsWhenSessionsExistsThenReturned() {
		Flux<ReactiveSessionInformation> sessions = this.sessionRegistry.getAllSessions("johndoe");
		StepVerifier.create(sessions)
			.assertNext((sessionInformation) -> assertThat(sessionInformation.getPrincipal()).isEqualTo("johndoe"))
			.assertNext((sessionInformation) -> assertThat(sessionInformation.getPrincipal()).isEqualTo("johndoe"))
			.assertNext((sessionInformation) -> assertThat(sessionInformation.getPrincipal()).isEqualTo("johndoe"))
			.verifyComplete();
	}

	@Test
	void getAllSessionsWhenInvalidateThenSessionsRemovedFromRepository() {
		this.sessionRegistry.getAllSessions("johndoe").flatMap(ReactiveSessionInformation::invalidate).blockLast();
		StepVerifier.create(this.sessionRepository.findById(johnSession1.getId())).expectComplete().verify();
		StepVerifier.create(this.sessionRepository.findById(johnSession2.getId())).expectComplete().verify();
		StepVerifier.create(this.sessionRepository.findById(johnSession3.getId())).expectComplete().verify();
	}

	static class StubIndexedSessionRepository implements ReactiveFindByIndexNameSessionRepository<MapSession> {

		Map<String, MapSession> johnSessions = Map.of(johnSession1.getId(), johnSession1, johnSession2.getId(),
				johnSession2, johnSession3.getId(), johnSession3);

		@Override
		public Mono<Map<String, MapSession>> findByIndexNameAndIndexValue(String indexName, String indexValue) {
			if ("johndoe".equals(indexValue)) {
				return Mono.just(this.johnSessions);
			}
			return Mono.empty();
		}

	}

}
