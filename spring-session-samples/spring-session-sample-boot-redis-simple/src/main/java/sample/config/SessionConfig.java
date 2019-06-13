/*
 * Copyright 2014-2019 the original author or authors.
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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.redis.SimpleRedisOperationsSessionRepository;

@EnableSpringHttpSession
public class SessionConfig {

	private final RedisConnectionFactory redisConnectionFactory;

	public SessionConfig(ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
		this.redisConnectionFactory = redisConnectionFactory.getIfAvailable();
	}

	@Bean
	public RedisOperations<String, Object> sessionRedisOperations() {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(this.redisConnectionFactory);
		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setHashKeySerializer(new StringRedisSerializer());
		return redisTemplate;
	}

	@Bean
	public SimpleRedisOperationsSessionRepository sessionRepository(
			RedisOperations<String, Object> sessionRedisOperations) {
		return new SimpleRedisOperationsSessionRepository(sessionRedisOperations);
	}

}
