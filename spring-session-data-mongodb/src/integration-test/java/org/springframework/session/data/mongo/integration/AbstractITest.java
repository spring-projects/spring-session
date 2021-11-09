/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session.data.mongo.integration;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Base class for repositories integration tests
 *
 * @author Jakub Kubrynski
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
public abstract class AbstractITest {

	protected SecurityContext context;

	protected SecurityContext changedContext;

	// @Autowired(required = false)
	// protected SessionEventRegistry registry;

	@BeforeEach
	void setup() {

		// if (this.registry != null) {
		// this.registry.clear();
		// }

		this.context = SecurityContextHolder.createEmptyContext();
		this.context.setAuthentication(new UsernamePasswordAuthenticationToken("username-" + UUID.randomUUID(), "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));

		this.changedContext = SecurityContextHolder.createEmptyContext();
		this.changedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
				"changedContext-" + UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));
	}

}
