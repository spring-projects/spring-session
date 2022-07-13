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

package org.springframework.session.aot.hint.server;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link WebSessionSecurityRuntimeHints}
 *
 * @author Marcus Da Coregio
 */
class WebSessionSecurityRuntimeHintsTests {

	private final RuntimeHints hints = new RuntimeHints();

	private final WebSessionSecurityRuntimeHints webSessionSecurityRuntimeHints = new WebSessionSecurityRuntimeHints();

	@Test
	void defaultCsrfTokenHasHints() {
		this.webSessionSecurityRuntimeHints.registerHints(this.hints, getClass().getClassLoader());
		assertThat(RuntimeHintsPredicates.serialization().onType(DefaultCsrfToken.class)).accepts(this.hints);
	}

	@Test
	void registerHintsWhenWebSessionMissingThenDoNotRegisterHints() {
		try (MockedStatic<ClassUtils> classUtilsMock = mockStatic(ClassUtils.class)) {
			classUtilsMock.when(() -> ClassUtils.isPresent(eq("org.springframework.web.server.WebSession"), any()))
					.thenReturn(false);
			this.webSessionSecurityRuntimeHints.registerHints(this.hints, getClass().getClassLoader());
			assertThat(this.hints.serialization().javaSerialization()).isEmpty();
		}
	}

	@Test
	void registerHintsWhenDefaultCsrfTokenMissingThenDoNotRegisterHints() {
		try (MockedStatic<ClassUtils> classUtilsMock = mockStatic(ClassUtils.class)) {
			classUtilsMock
					.when(() -> ClassUtils
							.isPresent(eq("org.springframework.security.web.server.csrf.DefaultCsrfToken"), any()))
					.thenReturn(false);
			this.webSessionSecurityRuntimeHints.registerHints(this.hints, getClass().getClassLoader());
			assertThat(this.hints.serialization().javaSerialization()).isEmpty();
		}
	}

	@Test
	void aotFactoriesContainsRegistrar() {
		boolean match = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
				.load(RuntimeHintsRegistrar.class).stream()
				.anyMatch((registrar) -> registrar instanceof WebSessionSecurityRuntimeHints);
		assertThat(match).isTrue();
	}

}
