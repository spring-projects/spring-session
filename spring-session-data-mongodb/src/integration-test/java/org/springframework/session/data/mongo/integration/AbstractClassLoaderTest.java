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

package org.springframework.session.data.mongo.integration;

import java.lang.reflect.Field;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JdkMongoSessionConverter;
import org.springframework.util.ReflectionUtils;

/**
 * Verify container's {@link ClassLoader} is injected into session converter (reactive and
 * traditional).
 *
 * @author Greg Turnquist
 */
public abstract class AbstractClassLoaderTest<T> extends AbstractITest {

	@Autowired
	T sessionRepository;

	@Autowired
	ApplicationContext applicationContext;

	@Test
	void verifyContainerClassLoaderLoadedIntoConverter() {

		Field mongoSessionConverterField = ReflectionUtils.findField(this.sessionRepository.getClass(),
				"mongoSessionConverter");
		ReflectionUtils.makeAccessible(mongoSessionConverterField);
		AbstractMongoSessionConverter sessionConverter = (AbstractMongoSessionConverter) ReflectionUtils
				.getField(mongoSessionConverterField, this.sessionRepository);

		AssertionsForClassTypes.assertThat(sessionConverter).isInstanceOf(JdkMongoSessionConverter.class);

		JdkMongoSessionConverter jdkMongoSessionConverter = (JdkMongoSessionConverter) sessionConverter;

		DeserializingConverter deserializingConverter = (DeserializingConverter) extractField(
				JdkMongoSessionConverter.class, "deserializer", jdkMongoSessionConverter);
		DefaultDeserializer deserializer = (DefaultDeserializer) extractField(DeserializingConverter.class,
				"deserializer", deserializingConverter);
		ClassLoader classLoader = (ClassLoader) extractField(DefaultDeserializer.class, "classLoader", deserializer);

		AssertionsForClassTypes.assertThat(classLoader).isEqualTo(this.applicationContext.getClassLoader());
	}

	private static Object extractField(Class<?> clazz, String fieldName, Object obj) {

		Field field = ReflectionUtils.findField(clazz, fieldName);
		ReflectionUtils.makeAccessible(field);
		return ReflectionUtils.getField(field, obj);
	}

}
