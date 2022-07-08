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

import java.util.function.Predicate;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsPredicates;
import org.springframework.aot.hint.SerializationHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.Assert;

/**
 * Generator of {@link SerializationHints} predicates, testing whether the given hints
 * match the expected behavior for serialization.
 *
 * @author Marcus Da Coregio
 * @see RuntimeHintsPredicates
 */
public class SerializationHintsPredicates {

	/**
	 * Return a predicate that checks whether a serialization hint is registered for the
	 * given type.
	 * @param typeReference the type
	 * @return the {@link RuntimeHints} predicate
	 */
	public TypeHintPredicate onType(TypeReference typeReference) {
		Assert.notNull(typeReference, "'typeReference' should not be null");
		return new TypeHintPredicate(typeReference);
	}

	/**
	 * Return a predicate that checks whether a serialization hint is registered for the
	 * given type.
	 * @param type the type
	 * @return the {@link RuntimeHints} predicate
	 */
	public TypeHintPredicate onType(Class<?> type) {
		Assert.notNull(type, "'type' should not be null");
		return new TypeHintPredicate(TypeReference.of(type));
	}

	public static class TypeHintPredicate implements Predicate<RuntimeHints> {

		private final TypeReference type;

		TypeHintPredicate(TypeReference type) {
			this.type = type;
		}

		@Override
		public boolean test(RuntimeHints hints) {
			return hints.serialization().javaSerialization().anyMatch((hint) -> hint.getType().equals(this.type));
		}

	}

}
