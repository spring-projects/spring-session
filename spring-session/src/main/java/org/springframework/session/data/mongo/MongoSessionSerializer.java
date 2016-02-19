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

import com.mongodb.DBObject;
import org.springframework.session.ExpiringSession;

/**
 * Interface for serializing and deserializing session objects.
 * To create custom serializer you have to implement this interface
 * and simply register your class as a bean.
 *
 * @author Jakub Kubrynski
 */
public interface MongoSessionSerializer {

	/**
	 * Serializes passed session to a Mongo {@code DBObject}
	 *
	 * @param session session to be saved
	 * @return object that will be directly saved into datastore
	 */
	DBObject serializeSession(ExpiringSession session);

	/**
	 * Deserializes retrieved session
	 *
	 * @param session session representation retrieved from datastore
	 * @return deserialized session
	 */
	ExpiringSession deserializeSession(DBObject session);

	/**
	 * Returns principal field name which is used by {@link org.springframework.session.FindByIndexNameSessionRepository}
	 * to retrieve sessions by principal name
	 *
	 * @return principal field name
	 */
	String getPrincipalFieldName();

	/**
	 * Returns field name containing last access time
	 *
	 * @return last access field name
	 */
	String getLastAccessedFieldName();
}
