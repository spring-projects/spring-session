package org.springframework.session.data.mongo;

import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Interface for serializing and deserializing session objects.
 * To create custom serializer you have to implement this interface
 * and simply register your class as a bean.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public interface MongoSessionConverter extends GenericConverter {

	Criteria getPrincipalQuery();

	String getExpiresAtFieldName();
}
