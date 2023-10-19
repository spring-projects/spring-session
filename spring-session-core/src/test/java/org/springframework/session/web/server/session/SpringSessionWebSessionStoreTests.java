/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.session.web.server.session;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.server.WebSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SpringSessionWebSessionStore}.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 */
@ExtendWith(MockitoExtension.class)
class SpringSessionWebSessionStoreTests<S extends Session> {

	@Mock(lenient = true)
	private ReactiveSessionRepository<S> sessionRepository;

	@Mock
	private S createSession;

	@Mock
	private S findByIdSession;

	private SpringSessionWebSessionStore<S> webSessionStore;

	@BeforeEach
	void setup() {
		this.webSessionStore = new SpringSessionWebSessionStore<>(this.sessionRepository);
		given(this.sessionRepository.findById(any())).willReturn(Mono.just(this.findByIdSession));
		given(this.sessionRepository.createSession()).willReturn(Mono.just(this.createSession));
	}

	@Test
	void constructorWhenNullRepositoryThenThrowsIllegalArgumentException() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SpringSessionWebSessionStore<S>(null))
			.withMessage("reactiveSessionRepository cannot be null");
	}

	@Test
	void createSessionWhenNoAttributesThenNotStarted() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		assertThat(createdWebSession.isStarted()).isFalse();
	}

	@Test
	void createSessionWhenAddAttributeThenStarted() {
		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		assertThat(createdWebSession.isStarted()).isTrue();
	}

	@Test
	void createSessionWhenGetAttributesAndSizeThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.size()).isEqualTo(0);

		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton("a"));

		assertThat(attributes.size()).isEqualTo(1);
	}

	@Test
	void createSessionWhenGetAttributesAndIsEmptyThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.isEmpty()).isTrue();

		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton("a"));

		assertThat(attributes.isEmpty()).isFalse();
	}

	@Test
	void createSessionWhenGetAttributesAndContainsKeyAndNotStringThenFalse() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.containsKey(1L)).isFalse();
	}

	@Test
	void createSessionWhenGetAttributesAndContainsKeyAndNotFoundThenFalse() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.containsKey("a")).isFalse();
	}

	@Test
	void createSessionWhenGetAttributesAndContainsKeyAndFoundThenTrue() {
		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.containsKey("a")).isTrue();
	}

	@Test
	void createSessionWhenGetAttributesAndPutThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.put("a", "b");

		verify(this.createSession).setAttribute("a", "b");
	}

	@Test
	void createSessionWhenGetAttributesAndPutNullThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.put("a", null);

		verify(this.createSession).setAttribute("a", null);
	}

	@Test
	void createSessionWhenGetAttributesAndRemoveThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.remove("a");

		verify(this.createSession).removeAttribute("a");
	}

	@Test
	void createSessionWhenGetAttributesAndPutAllThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.putAll(Collections.singletonMap("a", "b"));

		verify(this.createSession).setAttribute("a", "b");
	}

	@Test
	void createSessionWhenGetAttributesAndClearThenDelegatesToCreateSession() {
		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.clear();

		verify(this.createSession).removeAttribute("a");
	}

	@Test
	void createSessionWhenGetAttributesAndKeySetThenDelegatesToCreateSession() {
		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.keySet()).containsExactly("a");
	}

	@Test
	void createSessionWhenGetAttributesAndValuesThenDelegatesToCreateSession() {
		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton("a"));
		given(this.createSession.getAttribute("a")).willReturn("b");
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.values()).containsExactly("b");
	}

	@Test
	void createSessionWhenGetAttributesAndEntrySetThenDelegatesToCreateSession() {
		String attrName = "attrName";
		given(this.createSession.getAttributeNames()).willReturn(Collections.singleton(attrName));
		String attrValue = "attrValue";
		given(this.createSession.getAttribute(attrName)).willReturn(attrValue);
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		Set<Map.Entry<String, Object>> entries = attributes.entrySet();

		assertThat(entries).containsExactly(new AbstractMap.SimpleEntry<>(attrName, attrValue));
	}

	@Test
	void retrieveSessionThenStarted() {
		String id = "id";
		WebSession retrievedWebSession = this.webSessionStore.retrieveSession(id).block();

		assertThat(retrievedWebSession.isStarted()).isTrue();
		verify(this.findByIdSession).setLastAccessedTime(any());
	}

	@Test
	void removeSessionWhenInvokedThenSessionSaved() {
		String sessionId = "session-id";
		given(this.sessionRepository.deleteById(sessionId)).willReturn(Mono.empty());

		this.webSessionStore.removeSession(sessionId).block();

		verify(this.sessionRepository).deleteById(sessionId);
	}

	@Test
	void setClockWhenNullThenException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.webSessionStore.setClock(null))
			.withMessage("clock cannot be null");
	}

	@Test // gh-1114
	void createSessionThenSessionIsNotExpired() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();

		assertThat(createdWebSession.isExpired()).isFalse();
	}

	@Test // gh-1114
	void invalidateSessionThenSessionIsExpired() {
		WebSession createdWebSession = this.webSessionStore.createWebSession().block();
		given(createdWebSession.invalidate()).willReturn(Mono.empty());

		createdWebSession.invalidate().block();

		assertThat(createdWebSession.isExpired()).isTrue();
	}

}
