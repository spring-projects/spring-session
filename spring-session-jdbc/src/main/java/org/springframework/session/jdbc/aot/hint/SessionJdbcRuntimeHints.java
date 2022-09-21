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

package org.springframework.session.jdbc.aot.hint;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * A {@link RuntimeHintsRegistrar} for JDBC Session hints.
 *
 * @author Marcus Da Coregio
 */
class SessionJdbcRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.reflection().registerType(TypeReference.of("javax.sql.DataSource"),
				(hint) -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));
		hints.resources().registerPattern("org/springframework/session/jdbc/schema-db2.sql")
				.registerPattern("org/springframework/session/jdbc/schema-derby.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-db2.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-derby.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-h2.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-hsqldb.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-mysql.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-oracle.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-postgresql.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-sqlite.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-sqlserver.sql")
				.registerPattern("org/springframework/session/jdbc/schema-drop-sybase.sql")
				.registerPattern("org/springframework/session/jdbc/schema-h2.sql")
				.registerPattern("org/springframework/session/jdbc/schema-hsqldb.sql")
				.registerPattern("org/springframework/session/jdbc/schema-mysql.sql")
				.registerPattern("org/springframework/session/jdbc/schema-oracle.sql")
				.registerPattern("org/springframework/session/jdbc/schema-postgresql.sql")
				.registerPattern("org/springframework/session/jdbc/schema-sqlite.sql")
				.registerPattern("org/springframework/session/jdbc/schema-sqlserver.sql")
				.registerPattern("org/springframework/session/jdbc/schema-sybase.sql");
	}

}
