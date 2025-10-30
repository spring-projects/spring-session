/*
 * Copyright 2014-present the original author or authors.
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

package sample.config;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;

/**
 * @author jitendra on 3/3/16.
 */
// tag::class[]
@Configuration
public class SessionConfig implements BeanClassLoaderAware {

	private ClassLoader loader;

	/**
	 * Note that the bean name for this bean is intentionally
	 * {@code springSessionDefaultRedisSerializer}. It must be named this way to override
	 * the default {@link RedisSerializer} used by Spring Session.
	 */
	@Bean
	public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
		return new JacksonJsonRedisSerializer<>(objectMapper(), Object.class);
	}

	/**
	 * Customized {@link JsonMapper} to add mix-in for class that doesn't have default
	 * constructors
	 * @return the {@link JsonMapper} to use
	 */
	private JsonMapper objectMapper() {
		return JsonMapper.builder().addModules(SecurityJacksonModules.getModules(this.loader)).build();
	}

	/*
	 * @see
	 * org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang
	 * .ClassLoader)
	 */
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.loader = classLoader;
	}

}
// end::class[]
