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

package org.springframework.session.aot.hint.servlet;

import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.SavedCookie;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link HttpSessionSecurityRuntimeHints}
 *
 * @author Marcus Da Coregio
 */
class HttpSessionSecurityRuntimeHintsTests {

	private final RuntimeHints hints = new RuntimeHints();

	private final HttpSessionSecurityRuntimeHints httpSessionSecurityRuntimeHints = new HttpSessionSecurityRuntimeHints();

	@ParameterizedTest
	@MethodSource("getSerializationHintTypes")
	void httpSessionHasHints(TypeReference typeReference) {
		this.httpSessionSecurityRuntimeHints.registerHints(this.hints, getClass().getClassLoader());
		assertThat(RuntimeHintsPredicates.serialization().onType(typeReference)).accepts(this.hints);
	}

	@Test
	void registerHintsWhenHttpSessionMissingThenDoNotRegisterHints() {
		try (MockedStatic<ClassUtils> classUtilsMock = mockStatic(ClassUtils.class)) {
			classUtilsMock.when(() -> ClassUtils.isPresent(eq("jakarta.servlet.http.HttpSession"), any()))
					.thenReturn(false);
			this.httpSessionSecurityRuntimeHints.registerHints(this.hints, getClass().getClassLoader());
			assertThat(this.hints.serialization().javaSerializationHints()).isEmpty();
		}
	}

	@Test
	void registerHintsWhenDefaultCsrfTokenMissingThenDoNotRegisterHints() {
		try (MockedStatic<ClassUtils> classUtilsMock = mockStatic(ClassUtils.class)) {
			classUtilsMock.when(
					() -> ClassUtils.isPresent(eq("org.springframework.security.web.csrf.DefaultCsrfToken"), any()))
					.thenReturn(false);
			this.httpSessionSecurityRuntimeHints.registerHints(this.hints, getClass().getClassLoader());
			assertThat(this.hints.serialization().javaSerializationHints()).isEmpty();
		}
	}

	@Test
	void aotFactoriesContainsRegistrar() {
		boolean match = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
				.load(RuntimeHintsRegistrar.class).stream()
				.anyMatch((registrar) -> registrar instanceof HttpSessionSecurityRuntimeHints);
		assertThat(match).isTrue();
	}

	private static Stream<TypeReference> getSerializationHintTypes() {
		return Stream.of(TypeReference.of(TreeMap.class), TypeReference.of(Locale.class),
				TypeReference.of(DefaultSavedRequest.class), TypeReference.of(DefaultCsrfToken.class),
				TypeReference.of(WebAuthenticationDetails.class), TypeReference.of(SavedCookie.class),
				TypeReference.of("java.lang.String$CaseInsensitiveComparator"));
	}

}
