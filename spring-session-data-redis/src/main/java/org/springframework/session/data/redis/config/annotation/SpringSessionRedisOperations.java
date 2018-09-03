/*
 * Copyright 2014-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.redis.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Value;

/**
 * Annotation used to inject the Redis accessor used by Spring Session's Redis session
 * repository.
 *
 * @author Vedran Pavic
 * @see org.springframework.session.data.redis.RedisOperationsSessionRepository#getSessionRedisOperations()
 * @see org.springframework.session.data.redis.ReactiveRedisOperationsSessionRepository#getSessionRedisOperations()
 * @since 2.0.0
 */
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
		ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Value("#{sessionRepository.sessionRedisOperations}")
public @interface SpringSessionRedisOperations {

}
