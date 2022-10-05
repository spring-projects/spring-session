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
import java.util.Set;

import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.Document;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.lang.Nullable;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.IndexResolver;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.util.Assert;

/**
 * Base class for serializing and deserializing session objects. To create custom
 * serializer you have to implement this interface and simply register your class as a
 * bean.
 *
 * @author Jakub Kubrynski
 * @author Greg Turnquist
 * @since 1.2
 */
public abstract class AbstractMongoSessionConverter implements GenericConverter {

	static final String EXPIRE_AT_FIELD_NAME = "expireAt";

	private static final Log LOG = LogFactory.getLog(AbstractMongoSessionConverter.class);

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private IndexResolver<MongoSession> indexResolver = new DelegatingIndexResolver<>(
			new PrincipalNameIndexResolver<>());

	/**
	 * Returns query to be executed to return sessions based on a particular index.
	 * @param indexName name of the index
	 * @param indexValue value to query against
	 * @return built query or null if indexName is not supported
	 */
	@Nullable
	protected abstract Query getQueryForIndex(String indexName, Object indexValue);

	/**
	 * Method ensures that there is a TTL index on {@literal expireAt} field. It's has
	 * {@literal expireAfterSeconds} set to zero seconds, so the expiration time is
	 * controlled by the application. It can be extended in custom converters when there
	 * is a need for creating additional custom indexes.
	 * @param sessionCollectionIndexes {@link IndexOperations} to use
	 */
	protected void ensureIndexes(IndexOperations sessionCollectionIndexes) {

		for (IndexInfo info : sessionCollectionIndexes.getIndexInfo()) {
			if (EXPIRE_AT_FIELD_NAME.equals(info.getName())) {
				LOG.debug("TTL index on field " + EXPIRE_AT_FIELD_NAME + " already exists");
				return;
			}
		}

		LOG.info("Creating TTL index on field " + EXPIRE_AT_FIELD_NAME);

		sessionCollectionIndexes
				.ensureIndex(new Index(EXPIRE_AT_FIELD_NAME, Sort.Direction.ASC).named(EXPIRE_AT_FIELD_NAME).expire(0));
	}

	protected String extractPrincipal(MongoSession expiringSession) {

		return this.indexResolver.resolveIndexesFor(expiringSession)
				.get(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
	}

	public Set<ConvertiblePair> getConvertibleTypes() {

		return Collections.singleton(new ConvertiblePair(DBObject.class, MongoSession.class));
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {

		if (source == null) {
			return null;
		}

		if (DBObject.class.isAssignableFrom(sourceType.getType())) {
			return convert(new Document(((DBObject) source).toMap()));
		}
		else if (Document.class.isAssignableFrom(sourceType.getType())) {
			return convert((Document) source);
		}
		else {
			return convert((MongoSession) source);
		}
	}

	protected abstract DBObject convert(MongoSession session);

	protected abstract MongoSession convert(Document sessionWrapper);

	public void setIndexResolver(IndexResolver<MongoSession> indexResolver) {
		Assert.notNull(indexResolver, "indexResolver must not be null");
		this.indexResolver = indexResolver;
	}

}
