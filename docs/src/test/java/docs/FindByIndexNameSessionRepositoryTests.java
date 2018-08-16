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

package docs;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * @author Rob Winch
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class FindByIndexNameSessionRepositoryTests {
	@Mock
	FindByIndexNameSessionRepository<Session> sessionRepository;
	@Mock
	Session session;

	@Test
	public void setUsername() {
		// tag::set-username[]
		String username = "username";
		this.session.setAttribute(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
		// end::set-username[]
	}

	@Test
	@SuppressWarnings("unused")
	public void findByUsername() {
		// tag::findby-username[]
		String username = "username";
		Map<String, Session> sessionIdToSession = this.sessionRepository
				.findByPrincipalName(username);
		// end::findby-username[]
	}
}
