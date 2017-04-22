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

package docs.http;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.gemfire.config.annotation.PeerCacheApplication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Rob Winch
 * @author John Blum
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HttpSessionGemFireIndexingITests.Config.class)
public class HttpSessionGemFireIndexingITests {

	@Autowired
	private GemFireOperationsSessionRepository sessionRepository;

	@Test
	public void findByIndexName() {
		ExpiringSession session = this.sessionRepository.createSession();
		String username = "HttpSessionGemFireIndexingITests-findByIndexName-username";

		// tag::findbyindexname-set[]
		String indexName = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

		session.setAttribute(indexName, username);
		// end::findbyindexname-set[]

		this.sessionRepository.save(session);

		// tag::findbyindexname-get[]
		Map<String, ExpiringSession> idToSessions =
			this.sessionRepository.findByIndexNameAndIndexValue(indexName, username);
		// end::findbyindexname-get[]

		assertThat(idToSessions.keySet()).containsOnly(session.getId());

		this.sessionRepository.delete(session.getId());
	}

	@Test
	@WithMockUser("HttpSessionGemFireIndexingITests-findBySpringSecurityIndexName")
	public void findBySpringSecurityIndexName() {
		ExpiringSession session = this.sessionRepository.createSession();

		// tag::findbyspringsecurityindexname-context[]
		SecurityContext context = SecurityContextHolder.getContext();
		Authentication authentication = context.getAuthentication();
		// end::findbyspringsecurityindexname-context[]

		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		this.sessionRepository.save(session);

		// tag::findbyspringsecurityindexname-get[]
		String indexName = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;
		Map<String, ExpiringSession> idToSessions =
			this.sessionRepository.findByIndexNameAndIndexValue(indexName, authentication.getName());
		// end::findbyspringsecurityindexname-get[]

		assertThat(idToSessions.keySet()).containsOnly(session.getId());

		this.sessionRepository.delete(session.getId());
	}

	@PeerCacheApplication(name = "HttpSessionGemFireIndexingITests", logLevel = "error")
	@EnableGemFireHttpSession(regionName = "HttpSessionGemFireIndexingTestRegion")
	static class Config {
	}
}
