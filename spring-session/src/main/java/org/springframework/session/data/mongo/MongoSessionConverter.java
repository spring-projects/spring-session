package org.springframework.session.data.mongo;

import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Interface for serializing and deserializing session objects.
 * To create custom serializer you have to implement this interface
 * and simply register your class as a bean.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public interface MongoSessionConverter extends GenericConverter {

	/**
	 * Returns query to be executed to return sessions based on a particular index
	 * @param indexName name of the index
	 * @param indexValue value to query against
	 * @return built query or null if indexName is not supported
	 */
	Query getQueryForIndex(String indexName, Object indexValue);

	/**
	 * @return field name containing session validity timestamp
	 */
	String getExpiresAtFieldName();
}
