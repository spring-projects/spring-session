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

package org.springframework.session.data.redis.config.annotation.web.http;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Dynamically determines which session repository configuration to include using the
 * {@link EnableRedisHttpSession} annotation.
 *
 * @author Eleftheria Stein
 * @since 3.0
 */
final class RedisHttpSessionConfigurationSelector implements ImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata importMetadata) {
		if (!importMetadata.hasAnnotation(EnableRedisHttpSession.class.getName())) {
			return new String[0];
		}
		EnableRedisHttpSession annotation = importMetadata.getAnnotations().get(EnableRedisHttpSession.class)
				.synthesize();
		if (annotation.enableIndexingAndEvents()) {
			return new String[] { RedisIndexedHttpSessionConfiguration.class.getName() };
		}
		else {
			return new String[] { RedisHttpSessionConfiguration.class.getName() };
		}
	}

}
