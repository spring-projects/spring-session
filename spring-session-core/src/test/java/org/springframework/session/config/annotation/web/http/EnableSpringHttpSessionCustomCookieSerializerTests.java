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

package org.springframework.session.config.annotation.web.http;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SpringHttpSessionConfiguration} using a custom
 * {@link CookieSerializer}.
 *
 * @author Rob Winch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class EnableSpringHttpSessionCustomCookieSerializerTests {

	@Autowired
	private MockHttpServletRequest request;

	@Autowired
	private MockHttpServletResponse response;

	private MockFilterChain chain;

	@Autowired
	private SessionRepositoryFilter<? extends Session> sessionRepositoryFilter;

	@Autowired
	private SessionRepository sessionRepository;

	@Autowired
	private CookieSerializer cookieSerializer;

	@Before
	public void setup() {
		this.chain = new MockFilterChain();

		reset(this.sessionRepository);
		reset(this.cookieSerializer);
	}

	@Test
	public void usesReadSessionIds() throws Exception {
		String sessionId = "sessionId";
		given(this.cookieSerializer.readCookieValues(any(HttpServletRequest.class)))
				.willReturn(Collections.singletonList(sessionId));
		given(this.sessionRepository.findById(anyString()))
				.willReturn(new MapSession(sessionId));

		this.sessionRepositoryFilter.doFilter(this.request, this.response, this.chain);

		assertThat(getRequest().getRequestedSessionId()).isEqualTo(sessionId);
	}

	@Test
	public void usesWrite() throws Exception {
		given(this.sessionRepository.createSession()).willReturn(new MapSession());

		this.sessionRepositoryFilter.doFilter(this.request, this.response,
				new MockFilterChain() {

					@Override
					public void doFilter(ServletRequest request, ServletResponse response)
							throws IOException, ServletException {
						((HttpServletRequest) request).getSession();
						super.doFilter(request, response);
					}
				});

		verify(this.cookieSerializer).writeCookieValue(any(CookieValue.class));
	}

	private HttpServletRequest getRequest() {
		return (HttpServletRequest) this.chain.getRequest();
	}

	@EnableSpringHttpSession
	@Configuration
	static class Config {

		@Bean
		public SessionRepository sessionRepository() {
			return mock(SessionRepository.class);
		}

		@Bean
		public CookieSerializer cookieSerializer() {
			return mock(CookieSerializer.class);
		}

	}

}
