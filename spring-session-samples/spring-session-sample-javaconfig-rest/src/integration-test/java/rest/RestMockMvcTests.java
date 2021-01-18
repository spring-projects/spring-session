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

package rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import sample.SecurityConfig;
import sample.mvc.MvcConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.session.Session;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { RestMockMvcTests.Config.class, SecurityConfig.class, MvcConfig.class })
@WebAppConfiguration
class RestMockMvcTests {

	private static final String DOCKER_IMAGE = "redis:5.0.10";

	@Autowired
	private SessionRepositoryFilter<? extends Session> sessionRepositoryFilter;

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc;

	@BeforeEach
	void setup() {
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).alwaysDo(print())
				.addFilters(this.sessionRepositoryFilter).apply(springSecurity()).build();
	}

	@Test
	void noSessionOnNoCredentials() throws Exception {
		this.mvc.perform(get("/")).andExpect(header().doesNotExist("X-Auth-Token"))
				.andExpect(status().isUnauthorized());
	}

	@WithMockUser
	@Test
	void autheticatedAnnotation() throws Exception {
		this.mvc.perform(get("/")).andExpect(content().string("{\"username\":\"user\"}"));
	}

	@Test
	void autheticatedRequestPostProcessor() throws Exception {
		this.mvc.perform(get("/").with(user("user"))).andExpect(content().string("{\"username\":\"user\"}"));
	}

	@Configuration
	@EnableRedisHttpSession
	static class Config {

		@Bean
		GenericContainer redisContainer() {
			GenericContainer redisContainer = new GenericContainer(DOCKER_IMAGE).withExposedPorts(6379);
			redisContainer.start();
			return redisContainer;
		}

		@Bean
		LettuceConnectionFactory redisConnectionFactory() {
			return new LettuceConnectionFactory(redisContainer().getContainerIpAddress(),
					redisContainer().getFirstMappedPort());
		}

		@Bean
		HttpSessionIdResolver httpSessionIdResolver() {
			return HeaderHttpSessionIdResolver.xAuthToken();
		}

	}

}
