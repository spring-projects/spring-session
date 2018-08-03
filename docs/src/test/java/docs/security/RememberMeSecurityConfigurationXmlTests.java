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

package docs.security;

import java.time.Duration;
import java.util.Base64;

import javax.servlet.http.Cookie;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * @author rwinch
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
@SuppressWarnings("rawtypes")
public class RememberMeSecurityConfigurationXmlTests<T extends Session> {
	@Autowired
	WebApplicationContext context;
	@Autowired
	SessionRepositoryFilter springSessionRepositoryFilter;
	@Autowired
	SessionRepository<T> sessions;

	MockMvc mockMvc;

	@Before
	public void setup() {
		// @formatter:off
		this.mockMvc = MockMvcBuilders
				.webAppContextSetup(this.context)
				.addFilters(this.springSessionRepositoryFilter)
				.apply(springSecurity())
				.build();
		// @formatter:on
	}

	@Test
	public void authenticateWhenSpringSessionRememberMeEnabledThenCookieMaxAgeAndSessionExpirationSet()
			throws Exception {
		// @formatter:off
		MvcResult result = this.mockMvc
			.perform(formLogin())
			.andReturn();
		// @formatter:on

		Cookie cookie = result.getResponse().getCookie("SESSION");
		assertThat(cookie.getMaxAge()).isEqualTo(Integer.MAX_VALUE);
		T session = this.sessions
				.findById(new String(Base64.getDecoder().decode(cookie.getValue())));
		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(Duration.ofDays(30));

	}

}
// end::class[]
