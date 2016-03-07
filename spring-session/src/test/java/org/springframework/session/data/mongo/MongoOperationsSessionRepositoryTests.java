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
package org.springframework.session.data.mongo;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Jakub Kubrynski
 */
@RunWith(MockitoJUnitRunner.class)
public class MongoOperationsSessionRepositoryTests {

	@Mock
	MongoOperations mongoOperations;
	@Mock
	AbstractMongoSessionConverter converter;

	MongoOperationsSessionRepository sut;

	@Before
	public void setUp() throws Exception {
		this.sut = new MongoOperationsSessionRepository(this.mongoOperations);
		this.sut.setMongoSessionConverter(this.converter);
	}

	@Test
	public void shouldCreateSession() throws Exception {
		// when
		ExpiringSession session = this.sut.createSession();

		// then
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(MongoOperationsSessionRepository.DEFAULT_INACTIVE_INTERVAL);
	}

	@Test
	public void shouldSaveSession() throws Exception {
		// given
		MongoExpiringSession session = new MongoExpiringSession();
		BasicDBObject dbSession = new BasicDBObject();
		DBCollection collection = mock(DBCollection.class);

		given(this.converter.convert(session,
				TypeDescriptor.valueOf(MongoExpiringSession.class),
				TypeDescriptor.valueOf(DBObject.class))).willReturn(dbSession);
		given(this.mongoOperations
				.getCollection(MongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME))
						.willReturn(collection);
		// when
		this.sut.save(session);

		// then
		verify(collection).save(dbSession);
	}

	@Test
	public void shouldGetSession() throws Exception {
		// given
		String sessionId = UUID.randomUUID().toString();
		BasicDBObject dbSession = new BasicDBObject();
		given(this.mongoOperations.findById(sessionId, DBObject.class,
				MongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME))
						.willReturn(dbSession);
		MongoExpiringSession session = new MongoExpiringSession();
		given(this.converter.convert(dbSession, TypeDescriptor.valueOf(DBObject.class),
				TypeDescriptor.valueOf(MongoExpiringSession.class))).willReturn(session);

		// when
		ExpiringSession retrievedSession = this.sut.getSession(sessionId);

		// then
		assertThat(retrievedSession).isEqualTo(session);
	}

	@Test
	public void shouldHandleExpiredSession() throws Exception {
		// given
		String sessionId = UUID.randomUUID().toString();
		BasicDBObject dbSession = new BasicDBObject();
		given(this.mongoOperations.findById(sessionId, DBObject.class,
				MongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME))
						.willReturn(dbSession);
		MongoExpiringSession session = mock(MongoExpiringSession.class);
		given(session.isExpired()).willReturn(true);
		given(session.getId()).willReturn(sessionId);
		given(this.converter.convert(dbSession, TypeDescriptor.valueOf(DBObject.class),
				TypeDescriptor.valueOf(MongoExpiringSession.class))).willReturn(session);

		// when
		this.sut.getSession(sessionId);

		// then
		verify(this.mongoOperations).remove(any(DBObject.class),
				eq(MongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME));
	}

	@Test
	public void shouldDeleteSession() throws Exception {
		// given
		String sessionId = UUID.randomUUID().toString();

		// when
		this.sut.delete(sessionId);

		// then
		verify(this.mongoOperations).remove(any(DBObject.class),
				eq(MongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME));
	}

	@Test
	public void shouldGetSessionsMapByPrincipal() throws Exception {
		// given
		String principalNameIndexName = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

		DBObject dbSession = new BasicDBObject();
		given(this.converter.getQueryForIndex(anyString(), Matchers.anyObject()))
				.willReturn(mock(Query.class));
		given(this.mongoOperations.find(any(Query.class), eq(DBObject.class),
				eq(MongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME)))
						.willReturn(Collections.singletonList(dbSession));

		String sessionId = UUID.randomUUID().toString();

		MongoExpiringSession session = new MongoExpiringSession(sessionId, 1800);
		given(this.converter.convert(dbSession, TypeDescriptor.valueOf(DBObject.class),
				TypeDescriptor.valueOf(MongoExpiringSession.class))).willReturn(session);
		// when
		Map<String, MongoExpiringSession> sessionsMap = this.sut
				.findByIndexNameAndIndexValue(principalNameIndexName, "john");

		// then
		assertThat(sessionsMap).containsOnlyKeys(sessionId);
		assertThat(sessionsMap).containsValues(session);
	}

	@Test
	public void shouldReturnEmptyMapForNotSupportedIndex() throws Exception {
		// given
		String index = "some_not_supported_index_name";

		// when
		Map<String, MongoExpiringSession> sessionsMap = this.sut
				.findByIndexNameAndIndexValue(index, "some_value");

		// then
		assertThat(sessionsMap).isEmpty();
	}

}
