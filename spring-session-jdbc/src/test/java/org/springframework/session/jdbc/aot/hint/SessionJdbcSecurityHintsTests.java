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

import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsPredicates;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SessionJdbcSecurityHints}
 *
 * @author Marcus Da Coregio
 */
class SessionJdbcSecurityHintsTests {

	private final RuntimeHints hints = new RuntimeHints();

	private final SessionJdbcSecurityHints sessionJdbcSecurityHints = new SessionJdbcSecurityHints();

	@Test
	void aotFactoriesContainsRegistrar() {
		boolean match = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
				.load(RuntimeHintsRegistrar.class).stream()
				.anyMatch((registrar) -> registrar instanceof SessionJdbcSecurityHints);
		assertThat(match).isTrue();
	}

	@Test
	void jdbcSchemasHasHints() {
		this.sessionJdbcSecurityHints.registerHints(this.hints, getClass().getClassLoader());
		assertThat(RuntimeHintsPredicates.resource().forResource("org/springframework/session/jdbc/schema.sql"))
				.accepts(this.hints);
	}

	@Test
	void dataSourceHasHints() {
		this.sessionJdbcSecurityHints.registerHints(this.hints, getClass().getClassLoader());
		assertThat(RuntimeHintsPredicates.reflection().onType(TypeReference.of("javax.sql.DataSource")))
				.accepts(this.hints);
	}

}
