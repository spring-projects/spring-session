/*
 * Copyright 2014-2023 the original author or authors.
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

import java.time.Duration;
import java.util.UUID;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionDeletedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

/**
 * Tests for {@link ReactiveMongoSessionRepository}.
 *
 * @author Jakub Kubrynski
 * @author Vedran Pavic
 * @author Greg Turnquist
 */
@ExtendWith(MockitoExtension.class)
public class ReactiveMongoSessionRepositoryTest {

	@Mock
	private AbstractMongoSessionConverter converter;

	@Mock
	private ReactiveMongoOperations mongoOperations;

	@Mock
	private MongoOperations blockingMongoOperations;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ReactiveMongoSessionRepository repository;

	@BeforeEach
	void setUp() {

		this.repository = new ReactiveMongoSessionRepository(this.mongoOperations);
		this.repository.setMongoSessionConverter(this.converter);
		this.repository.setApplicationEventPublisher(this.eventPublisher);
	}

	@Test
	void shouldCreateSession() {

		this.repository.createSession() //
			.as(StepVerifier::create) //
			.expectNextMatches((mongoSession) -> {
				assertThat(mongoSession.getId()).isNotEmpty();
				assertThat(mongoSession.getMaxInactiveInterval())
					.isEqualTo(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS));
				return true;
			}) //
			.verifyComplete();
	}

	@Test
	void shouldSaveSession() {

		// given
		MongoSession session = new MongoSession();
		BasicDBObject dbSession = new BasicDBObject();

		given(this.converter.convert(session, TypeDescriptor.valueOf(MongoSession.class),
				TypeDescriptor.valueOf(DBObject.class)))
			.willReturn(dbSession);

		given(this.mongoOperations.save(dbSession, "sessions")).willReturn(Mono.just(dbSession));

		// when
		this.repository.save(session) //
			.as(StepVerifier::create) //
			.verifyComplete();

		verify(this.mongoOperations).save(dbSession, ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME);
	}

	@Test
	void shouldGetSession() {

		// given
		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
				ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME))
			.willReturn(Mono.just(sessionDocument));

		MongoSession session = new MongoSession();

		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class)))
			.willReturn(session);

		// when
		this.repository.findById(sessionId) //
			.as(StepVerifier::create) //
			.expectNext(session) //
			.verifyComplete();
	}

	@Test
	void shouldHandleExpiredSession() {

		// given
		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
				ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME))
			.willReturn(Mono.just(sessionDocument));

		given(this.mongoOperations.remove(sessionDocument, ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME))
			.willReturn(Mono.just(DeleteResult.acknowledged(1)));

		MongoSession session = mock(MongoSession.class);

		given(session.isExpired()).willReturn(true);
		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class)))
			.willReturn(session);

		// when
		this.repository.findById(sessionId) //
			.as(StepVerifier::create) //
			.verifyComplete();

		// then
		verify(this.mongoOperations).remove(any(Document.class),
				eq(ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME));
	}

	@Test
	void shouldDeleteSession() {

		// given
		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
				ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME))
			.willReturn(Mono.just(sessionDocument));

		given(this.mongoOperations.remove(sessionDocument, "sessions"))
			.willReturn(Mono.just(DeleteResult.acknowledged(1)));

		MongoSession session = mock(MongoSession.class);

		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class)))
			.willReturn(session);

		// when
		this.repository.deleteById(sessionId) //
			.as(StepVerifier::create) //
			.verifyComplete();

		verify(this.mongoOperations).remove(any(Document.class),
				eq(ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME));

		verify(this.eventPublisher).publishEvent(any(SessionDeletedEvent.class));
	}

	@Test
	void shouldInvokeMethodToCreateIndexesImperatively() {

		// given
		IndexOperations indexOperations = mock(IndexOperations.class);
		given(this.blockingMongoOperations.indexOps((String) any())).willReturn(indexOperations);

		this.repository.setBlockingMongoOperations(this.blockingMongoOperations);

		// when
		this.repository.afterPropertiesSet();

		// then
		verify(this.blockingMongoOperations, times(1)).indexOps((String) any());
		verify(this.converter, times(1)).ensureIndexes(indexOperations);
	}

	@Test
	void createSessionWhenSessionIdGeneratorThenUses() {
		this.repository.setSessionIdGenerator(() -> "test");

		this.repository.createSession().as(StepVerifier::create).assertNext((mongoSession) -> {
			assertThat(mongoSession.getId()).isEqualTo("test");
			assertThat(mongoSession.changeSessionId()).isEqualTo("test");
		}).verifyComplete();
	}

	@Test
	void setSessionIdGeneratorWhenNullThenThrowsException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setSessionIdGenerator(null))
			.withMessage("sessionIdGenerator cannot be null");
	}

	@Test
	void findByIdWhenChangeSessionIdThenUsesSessionIdGenerator() {
		this.repository.setSessionIdGenerator(() -> "test");

		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
				ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME))
			.willReturn(Mono.just(sessionDocument));

		MongoSession session = new MongoSession(sessionId);

		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class)))
			.willReturn(session);

		this.repository.findById(sessionId).as(StepVerifier::create).assertNext((mongoSession) -> {
			String oldId = mongoSession.getId();
			String newId = mongoSession.changeSessionId();
			assertThat(oldId).isEqualTo(sessionId);
			assertThat(newId).isEqualTo("test");
		}).verifyComplete();
	}

}
