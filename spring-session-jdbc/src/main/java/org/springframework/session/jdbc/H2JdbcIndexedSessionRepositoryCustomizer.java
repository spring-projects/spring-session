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

package org.springframework.session.jdbc;

import org.springframework.session.config.SessionRepositoryCustomizer;

/**
 * A {@link SessionRepositoryCustomizer} implementation that applies H2 specific optimized
 * SQL statements to {@link JdbcIndexedSessionRepository}.
 */
public class H2JdbcIndexedSessionRepositoryCustomizer
		implements SessionRepositoryCustomizer<JdbcIndexedSessionRepository> {

	private static final String CREATE_SESSION_ATTRIBUTE_QUERY = """
			MERGE INTO %TABLE_NAME%_ATTRIBUTES SA
			USING (
				SELECT CAST(? AS CHAR(36)) AS SESSION_PRIMARY_ID, CAST(? AS VARCHAR(200)) AS ATTRIBUTE_NAME, CAST(? AS LONGVARBINARY) AS ATTRIBUTE_BYTES
				FROM DUAL
			) A
			ON (SA.SESSION_PRIMARY_ID = A.SESSION_PRIMARY_ID and SA.ATTRIBUTE_NAME = A.ATTRIBUTE_NAME)
			WHEN MATCHED THEN
				UPDATE SET ATTRIBUTE_BYTES = A.ATTRIBUTE_BYTES
			WHEN NOT MATCHED THEN
				INSERT (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
				VALUES (A.SESSION_PRIMARY_ID, A.ATTRIBUTE_NAME, A.ATTRIBUTE_BYTES)
			""";

	@Override
	public void customize(JdbcIndexedSessionRepository sessionRepository) {
		sessionRepository.setCreateSessionAttributeQuery(CREATE_SESSION_ATTRIBUTE_QUERY);
	}

}
