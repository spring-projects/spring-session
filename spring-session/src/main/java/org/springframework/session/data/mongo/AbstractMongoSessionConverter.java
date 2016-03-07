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

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.IndexOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Base class for serializing and deserializing session objects. To create custom
 * serializer you have to implement this interface and simply register your class as a
 * bean.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public abstract class AbstractMongoSessionConverter implements GenericConverter {

	private static final Log LOG = LogFactory.getLog(AbstractMongoSessionConverter.class);

	protected static final String EXPIRE_AT_FIELD_NAME = "expireAt";

	/**
	 * Returns query to be executed to return sessions based on a particular index.
	 * @param indexName name of the index
	 * @param indexValue value to query against
	 * @return built query or null if indexName is not supported
	 */
	protected abstract Query getQueryForIndex(String indexName, Object indexValue);

	/**
	 * Method ensures that there is a TTL index on {@literal expireAt} field. It's has
	 * {@literal expireAfterSeconds} set to zero seconds, so the expiration time is
	 * controlled by the application.
	 *
	 * It can be extended in custom converters when there is a need for creating
	 * additional custom indexes.
	 * @param sessionCollectionIndexes {@link IndexOperations} to use
	 */
	protected void ensureIndexes(IndexOperations sessionCollectionIndexes) {
		List<IndexInfo> indexInfo = sessionCollectionIndexes.getIndexInfo();
		for (IndexInfo info : indexInfo) {
			if (EXPIRE_AT_FIELD_NAME.equals(info.getName())) {
				LOG.debug(
						"TTL index on field " + EXPIRE_AT_FIELD_NAME + " already exists");
				return;
			}
		}
		LOG.info("Creating TTL index on field " + EXPIRE_AT_FIELD_NAME);
		sessionCollectionIndexes
				.ensureIndex(new Index(EXPIRE_AT_FIELD_NAME, Sort.Direction.ASC)
						.named(EXPIRE_AT_FIELD_NAME).expire(0));
	}
}
