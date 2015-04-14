/*
 * Copyright 2002-2015 the original author or authors.
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
package sample;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.redis.embedded.EnableEmbeddedRedis;
import org.springframework.session.redis.embedded.RedisServerPort;
import org.springframework.session.web.http.HeaderHttpSessionStrategy;
import org.springframework.session.web.http.HttpSessionStrategy;

// tag::class[]
@Configuration
@EnableEmbeddedRedis // <1>
@EnableRedisHttpSession // <2>
public class HttpSessionConfig {

	@Bean
	public JedisConnectionFactory connectionFactory(@RedisServerPort int port) {
		JedisConnectionFactory factory = new JedisConnectionFactory();  // <3>
		factory.setPort(port);
		return factory;
	}

	@Bean
	public HttpSessionStrategy httpSessionStrategy() {
		return new HeaderHttpSessionStrategy(); // <4>
	}
}
// end::class[]