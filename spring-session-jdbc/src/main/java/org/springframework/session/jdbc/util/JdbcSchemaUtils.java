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

package org.springframework.session.jdbc.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * Utility class for schema files.
 *
 * @author Marcus da Coregio
 * @since 3.1
 */
public final class JdbcSchemaUtils {

	private JdbcSchemaUtils() {
	}

	/**
	 * Loads the content of the provided schema resource and replaces the
	 * {@link JdbcIndexedSessionRepository#DEFAULT_TABLE_NAME} by the provided table name.
	 * @param schemaResource the schema resource
	 * @param tableName the table name to replace
	 * @return the schema resource with the table name replaced
	 */
	public static Resource replaceDefaultTableName(Resource schemaResource, String tableName) throws IOException {
		try (InputStream inputStream = schemaResource.getInputStream()) {
			String schemaScript = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			String newSchema = schemaScript.replace(JdbcIndexedSessionRepository.DEFAULT_TABLE_NAME, tableName);
			return new ByteArrayResource(newSchema.getBytes(StandardCharsets.UTF_8));
		}
	}

}
