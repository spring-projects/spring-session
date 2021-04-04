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

package org.springframework.session.hazelcast;

import java.util.Arrays;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository.HazelcastSession;

import static org.assertj.core.api.Assertions.assertThat;

public class Hazelcast4IndexedSessionRepositoryWithInstanceTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String SESSION_MAP_NAME = "spring:session:sessions";

	private HazelcastInstance hzInstance;

	private Hazelcast4IndexedSessionRepository repository;

	@BeforeEach
	void initialize() {
		this.hzInstance = Hazelcast.newHazelcastInstance(new Config());

		this.repository = new Hazelcast4IndexedSessionRepository(this.hzInstance);
		this.repository.setFlushMode(FlushMode.IMMEDIATE);
		this.repository.setSaveMode(SaveMode.ALWAYS);
		this.repository.setSessionMapName(SESSION_MAP_NAME);
		this.repository.init();
	}

	@Test
	void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
		String principal = "username";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "notused",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContextImpl securityCtx = new SecurityContextImpl(authentication);

		HazelcastSession newSession = this.repository.createSession();
		newSession.setAttribute(SPRING_SECURITY_CONTEXT, securityCtx);

		IMap<String, MapSession> sessionsMap = this.hzInstance.getMap(SESSION_MAP_NAME);

		assertThat(sessionsMap).withFailMessage("SessionsMap is empty").size().isGreaterThan(0);

		assertThat(sessionsMap).withFailMessage("SessionEntry does not contain the expected attributes")
				.hasValueSatisfying(new Condition<>((session) -> session.getAttributeNames().containsAll(Arrays.asList(
						"org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME",
						"SPRING_SECURITY_CONTEXT")), "SessionHasExpectedAttributes"));
	}

}
