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

package org.springframework.session.data.gemfire;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.Query;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.pdx.PdxReader;
import com.gemstone.gemfire.pdx.PdxSerializable;
import com.gemstone.gemfire.pdx.PdxWriter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The GemFireOperationsSessionRepositoryIntegrationTests class is a test suite of test
 * cases testing the findByPrincipalName query method on the
 * GemFireOpeationsSessionRepository class.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.test.annotation.DirtiesContext
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.Region
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@DirtiesContext
@WebAppConfiguration
public class GemFireOperationsSessionRepositoryIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 300;

	private static final String GEMFIRE_LOG_LEVEL = "warning";
	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";
	private static final String SPRING_SESSION_GEMFIRE_REGION_NAME = "TestPartitionedSessions";

	SecurityContext context;

	SecurityContext changedContext;

	@Before
	public void setup() {
		this.context = SecurityContextHolder.createEmptyContext();
		this.context.setAuthentication(
				new UsernamePasswordAuthenticationToken("username-" + UUID.randomUUID(),
						"na", AuthorityUtils.createAuthorityList("ROLE_USER")));

		this.changedContext = SecurityContextHolder.createEmptyContext();
		this.changedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
				"changedContext-" + UUID.randomUUID(), "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));

		assertThat(this.gemfireCache).isNotNull();
		assertThat(this.gemfireSessionRepository).isNotNull();
		assertThat(this.gemfireSessionRepository.getMaxInactiveIntervalInSeconds())
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Region<Object, ExpiringSession> sessionRegion = this.gemfireCache
				.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertRegion(sessionRegion, SPRING_SESSION_GEMFIRE_REGION_NAME,
				DataPolicy.PARTITION);
		assertEntryIdleTimeout(sessionRegion, ExpirationAction.INVALIDATE,
				MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	protected Map<String, ExpiringSession> doFindByIndexNameAndIndexValue(
			String indexName, String indexValue) {
		return this.gemfireSessionRepository.findByIndexNameAndIndexValue(indexName,
				indexValue);
	}

	protected Map<String, ExpiringSession> doFindByPrincipalName(String principalName) {
		return doFindByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
				principalName);
	}

	@SuppressWarnings({ "unchecked" })
	protected Map<String, ExpiringSession> doFindByPrincipalName(String regionName,
			String principalName) {
		try {
			Region<String, ExpiringSession> region = this.gemfireCache
					.getRegion(regionName);

			assertThat(region).isNotNull();

			QueryService queryService = region.getRegionService().getQueryService();

			String queryString = String.format(
					GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
					region.getFullPath());

			Query query = queryService.newQuery(queryString);

			SelectResults<ExpiringSession> results = (SelectResults<ExpiringSession>) query
					.execute(new Object[] { principalName });

			Map<String, ExpiringSession> sessions = new HashMap<String, ExpiringSession>(
					results.size());

			for (ExpiringSession session : results.asList()) {
				sessions.put(session.getId(), session);
			}

			return sessions;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected boolean enableQueryDebugging() {
		return true;
	}

	protected ExpiringSession setAttribute(ExpiringSession session, String attributeName,
			Object attributeValue) {
		session.setAttribute(attributeName, attributeValue);
		return session;
	}

	@Test
	public void findSessionsByIndexedSessionAttributeNameValues() {
		ExpiringSession johnBlumSession = save(
				touch(setAttribute(createSession("johnBlum"), "vip", "yes")));
		ExpiringSession robWinchSession = save(
				touch(setAttribute(createSession("robWinch"), "vip", "yes")));
		ExpiringSession jonDoeSession = save(
				touch(setAttribute(createSession("jonDoe"), "vip", "no")));
		ExpiringSession pieDoeSession = save(
				touch(setAttribute(createSession("pieDoe"), "viper", "true")));
		ExpiringSession sourDoeSession = save(touch(createSession("sourDoe")));

		assertThat(get(johnBlumSession.getId())).isEqualTo(johnBlumSession);
		assertThat(johnBlumSession.getAttribute("vip")).isEqualTo("yes");
		assertThat(get(robWinchSession.getId())).isEqualTo(robWinchSession);
		assertThat(robWinchSession.getAttribute("vip")).isEqualTo("yes");
		assertThat(get(jonDoeSession.getId())).isEqualTo(jonDoeSession);
		assertThat(jonDoeSession.getAttribute("vip")).isEqualTo("no");
		assertThat(get(pieDoeSession.getId())).isEqualTo(pieDoeSession);
		assertThat(pieDoeSession.getAttributeNames().contains("vip")).isFalse();
		assertThat(get(sourDoeSession.getId())).isEqualTo(sourDoeSession);
		assertThat(sourDoeSession.getAttributeNames().contains("vip")).isFalse();

		Map<String, ExpiringSession> vipSessions = doFindByIndexNameAndIndexValue("vip",
				"yes");

		assertThat(vipSessions).isNotNull();
		assertThat(vipSessions.size()).isEqualTo(2);
		assertThat(vipSessions.get(johnBlumSession.getId())).isEqualTo(johnBlumSession);
		assertThat(vipSessions.get(robWinchSession.getId())).isEqualTo(robWinchSession);
		assertThat(vipSessions.containsKey(jonDoeSession.getId()));
		assertThat(vipSessions.containsKey(pieDoeSession.getId()));
		assertThat(vipSessions.containsKey(sourDoeSession.getId()));

		Map<String, ExpiringSession> nonVipSessions = doFindByIndexNameAndIndexValue(
				"vip", "no");

		assertThat(nonVipSessions).isNotNull();
		assertThat(nonVipSessions.size()).isEqualTo(1);
		assertThat(nonVipSessions.get(jonDoeSession.getId())).isEqualTo(jonDoeSession);
		assertThat(nonVipSessions.containsKey(johnBlumSession.getId()));
		assertThat(nonVipSessions.containsKey(robWinchSession.getId()));
		assertThat(nonVipSessions.containsKey(pieDoeSession.getId()));
		assertThat(nonVipSessions.containsKey(sourDoeSession.getId()));

		Map<String, ExpiringSession> noSessions = doFindByIndexNameAndIndexValue(
				"nonExistingAttribute", "test");

		assertThat(noSessions).isNotNull();
		assertThat(noSessions.isEmpty()).isTrue();
	}

	@Test
	public void findSessionsByPrincipalName() {
		ExpiringSession sessionOne = save(touch(createSession("robWinch")));
		ExpiringSession sessionTwo = save(touch(createSession("johnBlum")));
		ExpiringSession sessionThree = save(touch(createSession("robWinch")));
		ExpiringSession sessionFour = save(touch(createSession("johnBlum")));
		ExpiringSession sessionFive = save(touch(createSession("robWinch")));

		assertThat(get(sessionOne.getId())).isEqualTo(sessionOne);
		assertThat(get(sessionTwo.getId())).isEqualTo(sessionTwo);
		assertThat(get(sessionThree.getId())).isEqualTo(sessionThree);
		assertThat(get(sessionFour.getId())).isEqualTo(sessionFour);
		assertThat(get(sessionFive.getId())).isEqualTo(sessionFive);

		Map<String, ExpiringSession> johnBlumSessions = doFindByPrincipalName("johnBlum");

		assertThat(johnBlumSessions).isNotNull();
		assertThat(johnBlumSessions.size()).isEqualTo(2);
		assertThat(johnBlumSessions.containsKey(sessionOne.getId())).isFalse();
		assertThat(johnBlumSessions.containsKey(sessionThree.getId())).isFalse();
		assertThat(johnBlumSessions.containsKey(sessionFive.getId())).isFalse();
		assertThat(johnBlumSessions.get(sessionTwo.getId())).isEqualTo(sessionTwo);
		assertThat(johnBlumSessions.get(sessionFour.getId())).isEqualTo(sessionFour);

		Map<String, ExpiringSession> robWinchSessions = doFindByPrincipalName("robWinch");

		assertThat(robWinchSessions).isNotNull();
		assertThat(robWinchSessions.size()).isEqualTo(3);
		assertThat(robWinchSessions.containsKey(sessionTwo.getId())).isFalse();
		assertThat(robWinchSessions.containsKey(sessionFour.getId())).isFalse();
		assertThat(robWinchSessions.get(sessionOne.getId())).isEqualTo(sessionOne);
		assertThat(robWinchSessions.get(sessionThree.getId())).isEqualTo(sessionThree);
		assertThat(robWinchSessions.get(sessionFive.getId())).isEqualTo(sessionFive);
	}

	@Test
	public void findSessionsBySecurityPrincipalName() {
		ExpiringSession toSave = this.gemfireSessionRepository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		save(toSave);

		Map<String, ExpiringSession> findByPrincipalName = doFindByPrincipalName(
				getSecurityName());
		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findSessionsByChangedSecurityPrincipalName() {
		ExpiringSession toSave = this.gemfireSessionRepository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);
		save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.changedContext);
		save(toSave);

		Map<String, ExpiringSession> findByPrincipalName = doFindByPrincipalName(
				getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = doFindByPrincipalName(getChangedSecurityName());
		assertThat(findByPrincipalName).hasSize(1);
	}

	@Test
	public void findsNoSessionsByNonExistingPrincipal() {
		Map<String, ExpiringSession> nonExistingPrincipalSessions = doFindByPrincipalName(
				"nonExistingPrincipalName");

		assertThat(nonExistingPrincipalSessions).isNotNull();
		assertThat(nonExistingPrincipalSessions.isEmpty()).isTrue();
	}

	@Test
	public void findsNoSessionsAfterPrincipalIsRemoved() {
		String username = "doesNotFindAfterPrincipalRemoved";
		ExpiringSession session = save(touch(createSession(username)));
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
				null);
		save(session);

		Map<String, ExpiringSession> nonExistingPrincipalSessions = doFindByPrincipalName(
				username);

		assertThat(nonExistingPrincipalSessions).isNotNull();
		assertThat(nonExistingPrincipalSessions.isEmpty()).isTrue();
	}

	@Test
	public void saveAndReadSessionWithAttributes() {
		ExpiringSession expectedSession = this.gemfireSessionRepository.createSession();

		assertThat(expectedSession).isInstanceOf(
				AbstractGemFireOperationsSessionRepository.GemFireSession.class);

		((AbstractGemFireOperationsSessionRepository.GemFireSession) expectedSession)
				.setPrincipalName("jblum");

		List<String> expectedAttributeNames = Arrays.asList("booleanAttribute",
				"numericAttribute", "stringAttribute", "personAttribute");

		Person jonDoe = new Person("Jon", "Doe");

		expectedSession.setAttribute(expectedAttributeNames.get(0), true);
		expectedSession.setAttribute(expectedAttributeNames.get(1), Math.PI);
		expectedSession.setAttribute(expectedAttributeNames.get(2), "test");
		expectedSession.setAttribute(expectedAttributeNames.get(3), jonDoe);

		this.gemfireSessionRepository.save(touch(expectedSession));

		ExpiringSession savedSession = this.gemfireSessionRepository
				.getSession(expectedSession.getId());

		assertThat(savedSession).isEqualTo(expectedSession);
		assertThat(savedSession).isInstanceOf(
				AbstractGemFireOperationsSessionRepository.GemFireSession.class);
		assertThat(
				((AbstractGemFireOperationsSessionRepository.GemFireSession) savedSession)
						.getPrincipalName()).isEqualTo("jblum");

		assertThat(savedSession.getAttributeNames().containsAll(expectedAttributeNames))
				.as(String.format("Expected (%1$s); but was (%2$s)",
						expectedAttributeNames, savedSession.getAttributeNames()))
				.isTrue();

		assertThat(Boolean.valueOf(
				String.valueOf(savedSession.getAttribute(expectedAttributeNames.get(0)))))
						.isTrue();
		assertThat(Double.valueOf(
				String.valueOf(savedSession.getAttribute(expectedAttributeNames.get(1)))))
						.isEqualTo(Math.PI);
		assertThat(
				String.valueOf(savedSession.getAttribute(expectedAttributeNames.get(2))))
						.isEqualTo("test");
		assertThat(savedSession.getAttribute(expectedAttributeNames.get(3)))
				.isEqualTo(jonDoe);
	}

	private String getSecurityName() {
		return this.context.getAuthentication().getName();
	}

	private String getChangedSecurityName() {
		return this.changedContext.getAuthentication().getName();
	}

	@EnableGemFireHttpSession(regionName = SPRING_SESSION_GEMFIRE_REGION_NAME, maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionGemFireConfiguration {

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name",
					GemFireOperationsSessionRepositoryIntegrationTests.class.getName());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);

			return gemfireProperties;
		}

		@Bean
		CacheFactoryBean gemfireCache() {
			CacheFactoryBean gemfireCache = new CacheFactoryBean();

			gemfireCache.setClose(true);
			gemfireCache.setProperties(gemfireProperties());

			return gemfireCache;
		}
	}

	public static class Person implements Comparable<Person>, PdxSerializable {

		private String firstName;
		private String lastName;

		public Person() {
		}

		public Person(String firstName, String lastName) {
			this.firstName = validate(firstName);
			this.lastName = validate(lastName);
		}

		private String validate(String value) {
			Assert.hasText(value,
					String.format("The String value (%1$s) must be specified!", value));
			return value;
		}

		public String getFirstName() {
			return this.firstName;
		}

		public String getLastName() {
			return this.lastName;
		}

		public String getName() {
			return String.format("%1$s %2$s", getFirstName(), getLastName());
		}

		public void toData(PdxWriter pdxWriter) {
			pdxWriter.writeString("firstName", getFirstName());
			pdxWriter.writeString("lastName", getLastName());
		}

		public void fromData(final PdxReader pdxReader) {
			this.firstName = pdxReader.readString("firstName");
			this.lastName = pdxReader.readString("lastName");
		}

		@SuppressWarnings("all")
		public int compareTo(final Person person) {
			int compareValue = getLastName().compareTo(person.getLastName());
			return (compareValue != 0 ? compareValue
					: getFirstName().compareTo(person.getFirstName()));
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}

			if (!(obj instanceof Person)) {
				return false;
			}

			Person that = (Person) obj;

			return ObjectUtils.nullSafeEquals(this.getFirstName(), that.getFirstName())
					&& ObjectUtils.nullSafeEquals(this.getLastName(), that.getLastName());
		}

		@Override
		public int hashCode() {
			int hashValue = 17;
			hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(getFirstName());
			hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(getLastName());
			return hashValue;
		}

		@Override
		public String toString() {
			return getName();
		}
	}

}
