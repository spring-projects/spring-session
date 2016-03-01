/*
 * Copyright 2002-2016 the original author or authors.
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

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jakub Kubrynski
 */
@RunWith(MockitoJUnitRunner.class)
public class MongoOperationsSessionRepositoryTests {

	@Mock
	MongoOperations mongoOperations;
	@Mock
	GenericConverter converter;

	Integer maxInterval = 1800;
	String collectionName = "sessions";

	MongoOperationsSessionRepository sut;

	@Before
	public void setUp() throws Exception {
		sut = new MongoOperationsSessionRepository(mongoOperations, converter, maxInterval, collectionName);
	}

	@Test
	public void shouldCreateSession() throws Exception {
		//when
		ExpiringSession session = sut.createSession();

		//then
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(maxInterval);
	}

	@Test
	public void shouldSaveSession() throws Exception {
		//given
		MongoExpiringSession session = new MongoExpiringSession();
		BasicDBObject dbSession = new BasicDBObject();
		DBCollection collection = mock(DBCollection.class);

		when(converter.convert(session, TypeDescriptor.valueOf(MongoExpiringSession.class), TypeDescriptor.valueOf(DBObject.class))).thenReturn(dbSession);
		when(mongoOperations.getCollection(collectionName)).thenReturn(collection);
		//when
		sut.save(session);

		//then
		verify(collection).save(dbSession);
	}

	@Test
	public void shouldGetSession() throws Exception {
		//given
		String sessionId = UUID.randomUUID().toString();
		BasicDBObject dbSession = new BasicDBObject();
		when(mongoOperations.findById(sessionId, DBObject.class, collectionName)).thenReturn(dbSession);
		MongoExpiringSession session = new MongoExpiringSession();
		when(converter.convert(dbSession, TypeDescriptor.valueOf(DBObject.class), TypeDescriptor.valueOf(MongoExpiringSession.class))).thenReturn(session);

		//when
		ExpiringSession retrievedSession = sut.getSession(sessionId);

		//then
		assertThat(retrievedSession).isEqualTo(session);
	}

	@Test
	public void shouldHandleExpiredSession() throws Exception {
		//given
		String sessionId = UUID.randomUUID().toString();
		BasicDBObject dbSession = new BasicDBObject();
		when(mongoOperations.findById(sessionId, DBObject.class, collectionName)).thenReturn(dbSession);
		MongoExpiringSession session = mock(MongoExpiringSession.class);
		when(session.isExpired()).thenReturn(true);
		when(session.getId()).thenReturn(sessionId);
		when(converter.convert(dbSession, TypeDescriptor.valueOf(DBObject.class), TypeDescriptor.valueOf(MongoExpiringSession.class))).thenReturn(session);

		//when
		sut.getSession(sessionId);

		//then
		verify(mongoOperations).remove(any(DBObject.class), eq(collectionName));
	}

	@Test
	public void shouldDeleteSession() throws Exception {
		//given
		String sessionId = UUID.randomUUID().toString();

		//when
		sut.delete(sessionId);

		//then
		verify(mongoOperations).remove(any(DBObject.class), eq(collectionName));
	}

	@Test
	public void shouldGetSessionsMapByPrincipal() throws Exception {
		//given
		String principalNameIndexName = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

		DBObject dbSession = new BasicDBObject();
		when(mongoOperations.find(any(Query.class), eq(DBObject.class), eq(collectionName)))
				.thenReturn(Collections.singletonList(dbSession));

		String sessionId = UUID.randomUUID().toString();

		MongoExpiringSession session = new MongoExpiringSession(sessionId, 1800);
		when(converter.convert(dbSession, TypeDescriptor.valueOf(DBObject.class), TypeDescriptor.valueOf(MongoExpiringSession.class))).thenReturn(session);
		//when
		Map<String, MongoExpiringSession> sessionsMap = sut.findByIndexNameAndIndexValue(principalNameIndexName, "john");

		//then
		assertThat(sessionsMap).containsOnlyKeys(sessionId);
		assertThat(sessionsMap).containsValues(session);
	}

	@Test
	public void shouldReturnEmptyMapForNotSupportedIndex() throws Exception {
		//given
		String index = "some_not_supported_index_name";

		//when
		Map<String, MongoExpiringSession> sessionsMap = sut.findByIndexNameAndIndexValue(index, "some_value");

		//then
		assertThat(sessionsMap).isEmpty();
	}

}