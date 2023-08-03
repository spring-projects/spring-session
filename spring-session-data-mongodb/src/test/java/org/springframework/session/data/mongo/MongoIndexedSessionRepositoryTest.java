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

package org.springframework.session.data.mongo;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.SessionIdGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

/**
 * Tests for {@link MongoIndexedSessionRepository}.
 *
 * @author Jakub Kubrynski
 * @author Vedran Pavic
 * @author Greg Turnquist
 */
@ExtendWith(MockitoExtension.class)
public class MongoIndexedSessionRepositoryTest {

	@Mock
	private AbstractMongoSessionConverter converter;

	@Mock
	private MongoOperations mongoOperations;

	private MongoIndexedSessionRepository repository;

	@BeforeEach
	void setUp() {

		this.repository = new MongoIndexedSessionRepository(this.mongoOperations);
		this.repository.setMongoSessionConverter(this.converter);
	}

	@Test
	void shouldCreateSession() {

		// when
		MongoSession session = this.repository.createSession();

		// then
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getMaxInactiveInterval().getSeconds())
				.isEqualTo(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
	}

	@Test
	void shouldSaveSession() {

		// given
		MongoSession session = new MongoSession();
		BasicDBObject dbSession = new BasicDBObject();

		given(this.converter.convert(session, TypeDescriptor.valueOf(MongoSession.class),
				TypeDescriptor.valueOf(DBObject.class))).willReturn(dbSession);
		// when
		this.repository.save(session);

		// then
		verify(this.mongoOperations).save(dbSession, MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME);
	}

	@Test
	void shouldGetSession() {

		// given
		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
				MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME)).willReturn(sessionDocument);

		MongoSession session = new MongoSession();

		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class))).willReturn(session);

		// when
		MongoSession retrievedSession = this.repository.findById(sessionId);

		// then
		assertThat(retrievedSession).isEqualTo(session);
	}

	@Test
	void shouldHandleExpiredSession() {

		// given
		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
				MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME)).willReturn(sessionDocument);

		MongoSession session = mock(MongoSession.class);

		given(session.isExpired()).willReturn(true);
		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class))).willReturn(session);
		given(session.getId()).willReturn("sessionId");

		// when
		this.repository.findById(sessionId);

		// then
		verify(this.mongoOperations).remove(any(Document.class),
				eq(MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME));
	}

	@Test
	void shouldDeleteSession() {

		// given
		String sessionId = UUID.randomUUID().toString();

		Document sessionDocument = new Document();
		sessionDocument.put("id", sessionId);

		MongoSession mongoSession = new MongoSession(sessionId, MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class))).willReturn(mongoSession);
		given(this.mongoOperations.findById(eq(sessionId), eq(Document.class),
				eq(MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME))).willReturn(sessionDocument);

		// when
		this.repository.deleteById(sessionId);

		// then
		verify(this.mongoOperations).remove(any(Document.class),
				eq(MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME));
	}

	@Test
	void shouldGetSessionsMapByPrincipal() {

		// given
		String principalNameIndexName = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

		Document document = new Document();

		given(this.converter.getQueryForIndex(anyString(), any(Object.class))).willReturn(mock(Query.class));
		given(this.mongoOperations.find(any(Query.class), eq(Document.class),
				eq(MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME)))
						.willReturn(Collections.singletonList(document));

		String sessionId = UUID.randomUUID().toString();

		MongoSession session = new MongoSession(sessionId, 1800);

		given(this.converter.convert(document, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class))).willReturn(session);

		// when
		Map<String, MongoSession> sessionsMap = this.repository.findByIndexNameAndIndexValue(principalNameIndexName,
				"john");

		// then
		assertThat(sessionsMap).containsOnlyKeys(sessionId);
		assertThat(sessionsMap).containsValues(session);
	}

	@Test
	void shouldReturnEmptyMapForNotSupportedIndex() {

		// given
		String index = "some_not_supported_index_name";

		// when
		Map<String, MongoSession> sessionsMap = this.repository.findByIndexNameAndIndexValue(index, "some_value");

		// then
		assertThat(sessionsMap).isEmpty();
	}

	@Test
	void createSessionWhenSessionIdGenerationStrategyThenUses() {
		this.repository.setSessionIdGenerator(new FixedSessionIdGenerator("123"));
		MongoSession session = this.repository.createSession();
		assertThat(session.getId()).isEqualTo("123");
		assertThat(session.changeSessionId()).isEqualTo("123");
	}

	@Test
	void setSessionIdGenerationStrategyWhenNullThenThrowsException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setSessionIdGenerator(null));
	}

	@Test
	void findByIdWhenChangeSessionIdThenUsesSessionIdGenerationStrategy() {
		this.repository.setSessionIdGenerator(new FixedSessionIdGenerator("456"));

		Document sessionDocument = new Document();

		given(this.mongoOperations.findById("123", Document.class,
				MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME)).willReturn(sessionDocument);

		MongoSession session = new MongoSession("123");

		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class))).willReturn(session);

		MongoSession retrievedSession = this.repository.findById("123");
		assertThat(retrievedSession.getId()).isEqualTo("123");
		String newSessionId = retrievedSession.changeSessionId();
		assertThat(newSessionId).isEqualTo("456");
	}

	static class FixedSessionIdGenerator implements SessionIdGenerator {

		private final String id;

		FixedSessionIdGenerator(String id) {
			this.id = id;
		}

		@Override
		public String generate() {
			return this.id;
		}

	}

}
