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


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.session.ReactorSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rob Winch
 * @since 5.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SpringSessionWebSessionStoreTests<S extends Session> {
	@Mock
	ReactorSessionRepository<S> sessionRepository;
	@Mock
	S createSession;
	@Mock
	S findByIdSession;
	SpringSessionWebSessionStore<S> webSessionStore;

	@Before
	public void setup() {
		this.webSessionStore = new SpringSessionWebSessionStore<>(sessionRepository);
		when(this.sessionRepository.findById(any())).thenReturn(Mono.just(findByIdSession));
		when(this.sessionRepository.createSession()).thenReturn(Mono.just(createSession));
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorWhenNullRepositoryThenThrowsIllegalArgumentException() {
		new SpringSessionWebSessionStore<S>((ReactorSessionRepository<S>) null);
	}

	@Test
	public void createSessionWhenNoAttributesThenNotStarted() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		assertThat(createdWebSession.isStarted()).isFalse();
	}

	@Test
	public void createSessionWhenAddAttributeThenStarted() {
		when(createSession.getAttributeNames()).thenReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		assertThat(createdWebSession.isStarted()).isTrue();
	}

	@Test
	public void createSessionWhenGetAttributesAndSizeThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.size()).isEqualTo(0);

		when(this.createSession.getAttributeNames()).thenReturn(Collections.singleton("a"));

		assertThat(attributes.size()).isEqualTo(1);
	}

	@Test
	public void createSessionWhenGetAttributesAndIsEmptyThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.isEmpty()).isTrue();

		when(this.createSession.getAttributeNames()).thenReturn(Collections.singleton("a"));

		assertThat(attributes.isEmpty()).isFalse();
	}

	@Test
	public void createSessionWhenGetAttributesAndContainsKeyAndNotStringThenFalse() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.containsKey(1L)).isFalse();
	}

	@Test
	public void createSessionWhenGetAttributesAndContainsKeyAndNotFoundThenFalse() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.containsKey("a")).isFalse();
	}

	@Test
	public void createSessionWhenGetAttributesAndContainsKeyAndFoundThenTrue() {
		when(this.createSession.getAttributeNames()).thenReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.containsKey("a")).isTrue();
	}

	@Test
	public void createSessionWhenGetAttributesAndPutThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.put("a", "b");

		verify(createSession).setAttribute("a", "b");
	}

	@Test
	public void createSessionWhenGetAttributesAndPutNullThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.put("a", null);

		verify(createSession).setAttribute("a", null);
	}

	@Test
	public void createSessionWhenGetAttributesAndRemoveThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.remove("a");

		verify(createSession).removeAttribute("a");
	}

	@Test
	public void createSessionWhenGetAttributesAndPutAllThenDelegatesToCreateSession() {
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.putAll(Collections.singletonMap("a","b"));

		verify(createSession).setAttribute("a", "b");
	}

	@Test
	public void createSessionWhenGetAttributesAndClearThenDelegatesToCreateSession() {
		when(this.createSession.getAttributeNames()).thenReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		attributes.clear();

		verify(createSession).removeAttribute("a");
	}

	@Test
	public void createSessionWhenGetAttributesAndKeySetThenDelegatesToCreateSession() {
		when(this.createSession.getAttributeNames()).thenReturn(Collections.singleton("a"));
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.keySet()).containsExactly("a");
	}

	@Test
	public void createSessionWhenGetAttributesAndValuesThenDelegatesToCreateSession() {
		when(this.createSession.getAttributeNames()).thenReturn(Collections.singleton("a"));
		when(this.createSession.getAttribute("a")).thenReturn("b");
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();

		assertThat(attributes.values()).containsExactly("b");
	}

	@Test
	public void createSessionWhenGetAttributesAndEntrySetThenDelegatesToCreateSession() {
		String attrName = "attrName";
		when(createSession.getAttributeNames()).thenReturn(Collections.singleton(attrName));
		String attrValue = "attrValue";
		when(createSession.getAttribute(attrName)).thenReturn(attrValue);
		WebSession createdWebSession = this.webSessionStore.createSession().block();

		Map<String, Object> attributes = createdWebSession.getAttributes();
		Set<Map.Entry<String, Object>> entries = attributes.entrySet();

		assertThat(entries).containsExactly(new AbstractMap.SimpleEntry<String, Object>(attrName, attrValue));
	}

	@Test
	public void storeSessionWhenInvokedThenSessionSaved() {
		when(this.sessionRepository.save(this.createSession)).thenReturn(Mono.empty());
		WebSession createdSession = this.webSessionStore.createSession().block();

		this.webSessionStore.storeSession(createdSession).block();

		verify(this.sessionRepository).save(this.createSession);
	}

	@Test
	public void retrieveSessionThenStarted() {
		String id = "id";
		WebSession retrievedWebSession = this.webSessionStore.retrieveSession(id).block();

		assertThat(retrievedWebSession.isStarted()).isTrue();
	}

	@Test
	public void removeSessionWhenInvokedThenSessionSaved() {
		String sessionId = "session-id";
		when(this.sessionRepository.delete(sessionId)).thenReturn(Mono.empty());

		this.webSessionStore.removeSession(sessionId).block();

		verify(this.sessionRepository).delete(sessionId);
	}
}