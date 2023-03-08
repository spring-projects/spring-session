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
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JdbcSchemaUtils}.
 *
 * @author Marcus da Coregio
 */
class JdbcSchemaUtilsTests {

	@ParameterizedTest
	@MethodSource("getCreateSchemaFiles")
	void replaceCreateSchemaTableName(Resource schema) throws IOException {
		Resource newTableNameSchema = JdbcSchemaUtils.replaceDefaultTableName(schema, "NEW_TABLE_NAME");
		String schemaScript = new String(newTableNameSchema.getInputStream().readAllBytes());
		assertThat(schemaScript).doesNotContain("SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES", "SPRING_SESSION_IX1",
				"SPRING_SESSION_IX2", "SPRING_SESSION_IX3");
		assertThat(schemaScript).contains("NEW_TABLE_NAME", "NEW_TABLE_NAME_ATTRIBUTES", "NEW_TABLE_NAME_IX1",
				"NEW_TABLE_NAME_IX2", "NEW_TABLE_NAME_IX3");
	}

	@ParameterizedTest
	@MethodSource("getDropSchemaFiles")
	void replaceDropSchemaTableName(Resource schema) throws IOException {
		Resource newTableNameSchema = JdbcSchemaUtils.replaceDefaultTableName(schema, "NEW_TABLE_NAME");
		String schemaScript = new String(newTableNameSchema.getInputStream().readAllBytes());
		assertThat(schemaScript).doesNotContain("SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES");
		assertThat(schemaScript).contains("NEW_TABLE_NAME", "NEW_TABLE_NAME_ATTRIBUTES");
	}

	private static Stream<Resource> getCreateSchemaFiles() throws IOException {
		return getSchemaFiles().filter((resource) -> !resource.getFilename().contains("drop"));
	}

	private static Stream<Resource> getDropSchemaFiles() throws IOException {
		return getSchemaFiles().filter((resource) -> resource.getFilename().contains("drop"));
	}

	private static Stream<Resource> getSchemaFiles() throws IOException {
		return Arrays.stream(new PathMatchingResourcePatternResolver()
				.getResources("classpath*:org/springframework/session/jdbc/schema-*.sql"));
	}

}
