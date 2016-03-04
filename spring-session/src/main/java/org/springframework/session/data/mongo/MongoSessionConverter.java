package org.springframework.session.data.mongo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.IndexOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * Base class for serializing and deserializing session objects.
 * To create custom serializer you have to implement this interface
 * and simply register your class as a bean.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public abstract class MongoSessionConverter implements GenericConverter {

	private static final Log LOG = LogFactory.getLog(MongoSessionConverter.class);

	protected static final String EXPIRE_AT_FIELD_NAME = "expireAt";

	/**
	 * Returns query to be executed to return sessions based on a particular index
	 * @param indexName name of the index
	 * @param indexValue value to query against
	 * @return built query or null if indexName is not supported
	 */
	protected abstract Query getQueryForIndex(String indexName, Object indexValue);

	/**
	 * Method ensures that there is a TTL index on {@literal expireAt} field.
	 * It's has {@literal expireAfterSeconds} set to zero seconds, so the expiration
	 * time is controlled by the application.
	 *
	 * It can be extended in custom converters when there is a need for creating
	 * additional custom indexes.
	 */
	protected void ensureIndexes(IndexOperations sessionCollectionIndexes) {
		List<IndexInfo> indexInfo = sessionCollectionIndexes.getIndexInfo();
		for (IndexInfo info : indexInfo) {
			if (EXPIRE_AT_FIELD_NAME.equals(info.getName())) {
				LOG.debug("TTL index on field " + EXPIRE_AT_FIELD_NAME + " already exists");
				return;
			}
		}
		LOG.info("Creating TTL index on field " + EXPIRE_AT_FIELD_NAME);
		sessionCollectionIndexes
				.ensureIndex(new Index(EXPIRE_AT_FIELD_NAME, Sort.Direction.ASC).named(EXPIRE_AT_FIELD_NAME).expire(0));
	}
}
