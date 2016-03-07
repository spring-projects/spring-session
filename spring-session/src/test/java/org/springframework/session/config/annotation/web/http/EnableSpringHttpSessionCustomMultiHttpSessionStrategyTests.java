/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session.config.annotation.web.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.web.http.MultiHttpSessionStrategy;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Rob Winch
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class EnableSpringHttpSessionCustomMultiHttpSessionStrategyTests {
	@Autowired
	MockHttpServletRequest request;
	@Autowired
	MockHttpServletResponse response;

	MockFilterChain chain;

	@Autowired
	SessionRepositoryFilter<? extends ExpiringSession> sessionRepositoryFilter;

	@Autowired
	MultiHttpSessionStrategy strategy;

	@Before
	public void setup() {
		this.chain = new MockFilterChain();
	}

	@Test
	public void wrapRequestAndResponseUsed() throws Exception {
		given(this.strategy.wrapRequest(any(HttpServletRequest.class),
				any(HttpServletResponse.class))).willReturn(this.request);
		given(this.strategy.wrapResponse(any(HttpServletRequest.class),
				any(HttpServletResponse.class))).willReturn(this.response);

		this.sessionRepositoryFilter.doFilter(this.request, this.response, this.chain);

		verify(this.strategy).wrapRequest(any(HttpServletRequest.class),
				any(HttpServletResponse.class));
		verify(this.strategy).wrapResponse(any(HttpServletRequest.class),
				any(HttpServletResponse.class));
	}

	@EnableSpringHttpSession
	@Configuration
	static class Config {
		@Bean
		public MapSessionRepository mapSessionRepository() {
			return new MapSessionRepository();
		}

		@Bean
		public MultiHttpSessionStrategy strategy() {
			return mock(MultiHttpSessionStrategy.class);
		}
	}
}
