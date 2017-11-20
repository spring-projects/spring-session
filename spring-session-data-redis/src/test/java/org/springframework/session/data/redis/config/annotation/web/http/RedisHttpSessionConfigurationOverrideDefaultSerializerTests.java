/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.data.redis.config.annotation.web.http;

import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Rob Winch
 * @author Mark Paluch
 *
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class RedisHttpSessionConfigurationOverrideDefaultSerializerTests {

	@SpringSessionRedisOperations
	RedisTemplate<Object, Object> template;

	@Autowired
	RedisSerializer<Object> defaultRedisSerializer;

	@Test
	public void overrideDefaultRedisTemplate() {
		assertThat(this.template.getDefaultSerializer())
				.isSameAs(this.defaultRedisSerializer);
	}

	@EnableRedisHttpSession
	@Configuration
	static class Config {
		@Bean
		@SuppressWarnings("unchecked")
		public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
			return mock(RedisSerializer.class);
		}

		@Bean
		public RedisConnectionFactory connectionFactory() {
			RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
			RedisConnection connection = mock(RedisConnection.class);
			given(factory.getConnection()).willReturn(connection);
			given(connection.getConfig(anyString())).willReturn(new Properties());

			return factory;
		}
	}
}
