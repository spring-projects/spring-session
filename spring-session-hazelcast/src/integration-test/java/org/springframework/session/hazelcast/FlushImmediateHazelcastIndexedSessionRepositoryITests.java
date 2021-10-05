/*
 * Copyright 2014-2021 the original author or authors.
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

package org.springframework.session.hazelcast;

import java.util.Map;

import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FlushMode;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HazelcastIndexedSessionRepository} using embedded
 * topology, with flush mode set to immediate.
 *
 * @author Eleftheria Stein
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class FlushImmediateHazelcastIndexedSessionRepositoryITests {

	@Autowired
	private HazelcastIndexedSessionRepository repository;

	@Test
	void createSessionWithSecurityContextAndFindByPrincipalName() {
		String username = "saves-" + System.currentTimeMillis();

		HazelcastIndexedSessionRepository.HazelcastSession session = this.repository.createSession();
		String sessionId = session.getId();

		Authentication authentication = new UsernamePasswordAuthenticationToken(username, "password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		securityContext.setAuthentication(authentication);
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);

		this.repository.save(session);

		Map<String, HazelcastIndexedSessionRepository.HazelcastSession> findByPrincipalName = this.repository
				.findByPrincipalName(username);

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(sessionId);

		this.repository.deleteById(sessionId);
	}

	@EnableHazelcastHttpSession(flushMode = FlushMode.IMMEDIATE)
	@Configuration
	static class HazelcastSessionConfig {

		@Bean
		HazelcastInstance hazelcastInstance() {
			return HazelcastITestUtils.embeddedHazelcastServer();
		}

	}

}
