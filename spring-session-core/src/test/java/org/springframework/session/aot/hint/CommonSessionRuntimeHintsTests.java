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

package org.springframework.session.aot.hint;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CommonSessionRuntimeHints}
 *
 * @author Marcus Da Coregio
 */
class CommonSessionRuntimeHintsTests {

	private final RuntimeHints hints = new RuntimeHints();

	private final CommonSessionRuntimeHints commonSessionRuntimeHints = new CommonSessionRuntimeHints();

	@ParameterizedTest
	@MethodSource("getSerializationHintTypes")
	void commonSessionTypesHasHints(TypeReference typeReference) {
		this.commonSessionRuntimeHints.registerHints(this.hints, getClass().getClassLoader());
		assertThat(RuntimeHintsPredicates.serialization().onType(typeReference)).accepts(this.hints);
	}

	@Test
	void aotFactoriesContainsRegistrar() {
		boolean match = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
				.load(RuntimeHintsRegistrar.class).stream()
				.anyMatch((registrar) -> registrar instanceof CommonSessionRuntimeHints);
		assertThat(match).isTrue();
	}

	private static Stream<TypeReference> getSerializationHintTypes() {
		return Stream.of(TypeReference.of(String.class), TypeReference.of(ArrayList.class),
				TypeReference.of(TreeSet.class), TypeReference.of(Number.class), TypeReference.of(Long.class),
				TypeReference.of(Integer.class), TypeReference.of(StackTraceElement.class),
				TypeReference.of(Throwable.class), TypeReference.of(Exception.class),
				TypeReference.of(RuntimeException.class),
				TypeReference.of("java.util.Collections$UnmodifiableCollection"),
				TypeReference.of("java.util.Collections$UnmodifiableList"),
				TypeReference.of("java.util.Collections$EmptyList"),
				TypeReference.of("java.util.Collections$UnmodifiableRandomAccessList"),
				TypeReference.of("java.util.Collections$UnmodifiableSet"));
	}

}
