/*
 * Copyright 2014-2020 the original author or authors.
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

package org.springframework.session.hazelcast.config.annotation.web.http;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * Dynamically determines which Hazelcast configuration class to include.
 *
 * @author Eleftheria Stein
 * @since 2.4.0
 */
class EnableHazelcastHttpSessionSelector implements ImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		ClassLoader classLoader = getClass().getClassLoader();
		boolean hazelcast4OnClasspath = ClassUtils.isPresent("com.hazelcast.map.IMap", classLoader);
		List<String> classNames = new ArrayList<>(1);
		if (hazelcast4OnClasspath) {
			classNames.add(Hazelcast4HttpSessionConfiguration.class.getName());
		}
		else {
			classNames.add(HazelcastHttpSessionConfiguration.class.getName());
		}
		return classNames.toArray(new String[0]);
	}

}
