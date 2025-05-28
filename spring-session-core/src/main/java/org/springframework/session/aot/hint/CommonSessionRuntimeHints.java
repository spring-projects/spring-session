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

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TreeSet;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.session.MapSession;

/**
 * A {@link RuntimeHintsRegistrar} for common session hints.
 *
 * @author Marcus Da Coregio
 */
class CommonSessionRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		Arrays.asList(TypeReference.of(String.class), TypeReference.of(Number.class), TypeReference.of(Long.class),
				TypeReference.of(Integer.class), TypeReference.of(URL.class), TypeReference.of(Instant.class),
				TypeReference.of(Duration.class), TypeReference.of("java.time.Ser"),
				TypeReference.of(StackTraceElement.class), TypeReference.of(Throwable.class),
				TypeReference.of(Exception.class), TypeReference.of(RuntimeException.class),
				TypeReference.of(ArrayList.class), TypeReference.of(TreeSet.class), TypeReference.of(HashMap.class),
				TypeReference.of(LinkedHashMap.class), TypeReference.of(HashSet.class),
				TypeReference.of(LinkedHashSet.class), TypeReference.of("java.util.Collections$UnmodifiableCollection"),
				TypeReference.of("java.util.Collections$UnmodifiableList"),
				TypeReference.of("java.util.Collections$EmptyList"),
				TypeReference.of("java.util.Collections$UnmodifiableRandomAccessList"),
				TypeReference.of("java.util.Collections$UnmodifiableSet"),
				TypeReference.of("java.util.Collections$UnmodifiableMap"), TypeReference.of(MapSession.class))
			.forEach(hints.serialization()::registerType);
	}

}
