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

package rest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import sample.HttpSessionConfig;
import sample.SecurityConfig;
import sample.mvc.MvcConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.session.ExpiringSession;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { HttpSessionConfig.class, SecurityConfig.class,
		MvcConfig.class })
@WebAppConfiguration
public class RestMockMvcTests {

	@Autowired
	SessionRepositoryFilter<? extends ExpiringSession> sessionRepositoryFilter;

	@Autowired
	WebApplicationContext context;

	MockMvc mvc;

	@Before
	public void setup() {
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).alwaysDo(print())
				.addFilters(this.sessionRepositoryFilter).apply(springSecurity()).build();
	}

	@Test
	public void noSessionOnNoCredentials() throws Exception {
		this.mvc.perform(get("/")).andExpect(header().doesNotExist("X-Auth-Token"))
				.andExpect(status().isUnauthorized());
	}

	@WithMockUser
	@Test
	public void autheticatedAnnotation() throws Exception {
		this.mvc.perform(get("/")).andExpect(content().string("{\"username\":\"user\"}"));
	}

	@Test
	public void autheticatedRequestPostProcessor() throws Exception {
		this.mvc.perform(get("/").with(user("user")))
				.andExpect(content().string("{\"username\":\"user\"}"));
	}

}
