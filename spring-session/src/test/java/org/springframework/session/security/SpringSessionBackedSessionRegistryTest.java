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

package org.springframework.session.security;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.userdetails.User;

import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpringSessionBackedSessionRegistryTest {

	static final String SESSION_ID = "sessionId";
	static final String USER_NAME = "userName";
	static final Date NOW = new Date();

	@Mock
	FindByIndexNameSessionRepository<ExpiringSession> sessionRepository;

	@InjectMocks
	SpringSessionBackedSessionRegistry sessionRegistry;

	@Test
	public void sessionInformationForExistingSession() {
		ExpiringSession session = createSession(SESSION_ID, USER_NAME, NOW.getTime());
		when(this.sessionRepository.getSession(SESSION_ID)).thenReturn(session);

		SessionInformation sessionInfo = this.sessionRegistry.getSessionInformation(SESSION_ID);

		assertThat(sessionInfo.getSessionId()).isEqualTo(SESSION_ID);
		assertThat(sessionInfo.getLastRequest()).isEqualTo(NOW);
		assertThat(sessionInfo.getPrincipal()).isEqualTo(USER_NAME);
		assertThat(sessionInfo.isExpired()).isFalse();
	}

	@Test
	public void noSessionInformationForMissingSession() {
		assertThat(this.sessionRegistry.getSessionInformation("nonExistingSessionId")).isNull();
	}

	@Test
	public void getAllSessions() {
		ExpiringSession session1 = createSession(SESSION_ID, USER_NAME, NOW.getTime());
		ExpiringSession session2 = createSession("OtherSession", USER_NAME, NOW.getTime());
		Map<String, ExpiringSession> sessions = new LinkedHashMap<String, ExpiringSession>();
		sessions.put(session1.getId(), session1);
		sessions.put(session2.getId(), session2);
		when(this.sessionRepository.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, USER_NAME)).thenReturn(sessions);

		User principal = new User(USER_NAME, "password", Collections.<GrantedAuthority>emptyList());
		List<SessionInformation> sessionInfos = this.sessionRegistry.getAllSessions(principal, false);

		assertThat(sessionInfos).hasSize(2);
		assertThat(sessionInfos.get(0).getSessionId()).isEqualTo(SESSION_ID);
		assertThat(sessionInfos.get(1).getSessionId()).isEqualTo("OtherSession");
	}

	@Test
	public void expireNow() {
		ExpiringSession session = createSession(SESSION_ID, USER_NAME, NOW.getTime());
		when(this.sessionRepository.getSession(SESSION_ID)).thenReturn(session);

		SessionInformation sessionInfo = this.sessionRegistry.getSessionInformation(SESSION_ID);
		assertThat(sessionInfo.isExpired()).isFalse();

		sessionInfo.expireNow();

		assertThat(sessionInfo.isExpired()).isTrue();
		verify(this.sessionRepository).delete(SESSION_ID);
	}

	private ExpiringSession createSession(String sessionId, String userName, Long lastAccessed) {
		MapSession session = new MapSession(sessionId);
		session.setLastAccessedTime(lastAccessed);
		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn(userName);
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(authentication);
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		return session;
	}

}
