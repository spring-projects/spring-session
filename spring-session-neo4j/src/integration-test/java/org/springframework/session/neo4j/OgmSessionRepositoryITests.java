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

package org.springframework.session.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.ogm.config.Credentials;
import org.neo4j.ogm.session.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.neo4j.config.annotation.web.http.EnableOgmHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests for {@link OgmSessionRepository} using a Neo4j database.
 *
 * @author Eric Spiegelberg
 */
@WebAppConfiguration
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class OgmSessionRepositoryITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";
	
	private static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	private SessionFactory sessionFactory;
	
	@Autowired
	private OgmSessionRepository repository;
	
	private SecurityContext context;

	private SecurityContext changedContext;
	
	@Before
	public void setup() throws Exception {
		// TODO: Remove use of System.out
		String username = "username-" + UUID.randomUUID();
		System.out.println("Setting context's username to '" + username + "'");
		this.context = SecurityContextHolder.createEmptyContext();
		this.context.setAuthentication(
				new UsernamePasswordAuthenticationToken(username,
						"na", AuthorityUtils.createAuthorityList("ROLE_USER")));

		this.changedContext = SecurityContextHolder.createEmptyContext();
		this.changedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
				"changedContext-" + UUID.randomUUID(), "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));
	}
	
	@EnableOgmHttpSession
	protected static class BaseConfig {

		@Value("${neo4.connectionPoolSize:15}")
		protected int neo4jConnectionPoolSize;
		
		@Value("${spring.data.neo4j.uri:bolt://neo4j:dev@localhost}")
		protected String neo4jUri;
		
		@Value("${neo4.driver:org.neo4j.ogm.drivers.bolt.driver.BoltDriver}")
		protected String neo4jDriver;
		
		@Bean
		public org.neo4j.ogm.config.Configuration configureOgm() {
	    
			org.neo4j.ogm.config.Configuration configuration = new org.neo4j.ogm.config.Configuration.Builder().uri(neo4jUri).build();
			Credentials credentials = configuration.getCredentials();
			
	        return configuration;
	        
		}
		
		@Bean
		//@Qualifier("springSessionOgmSessionFactory")
		public SessionFactory sessionFactory() {	
			
			org.neo4j.ogm.config.Configuration configuration = configureOgm();
			SessionFactory sessionFactory = new SessionFactory(configuration, "school.domain");
			return sessionFactory;
		}

	}

	@Test
	public void saves() throws InterruptedException {

		OgmSessionRepository.OgmSession toSave = this.repository
				.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";		
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		
//		String username = "saves-" + System.currentTimeMillis();
//		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
//		Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username,
//				"password", AuthorityUtils.createAuthorityList("ROLE_USER"));
//		
//		toSaveContext.setAuthentication(toSaveToken);
		//toSave.setAttribute(SPRING_SECURITY_CONTEXT, toSaveContext);
		//toSave.setAttribute(INDEX_NAME, username);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);
		toSave.setAttribute(INDEX_NAME, context.getAuthentication().getPrincipal().toString());
		
		this.repository.save(toSave);

		String toSaveId = toSave.getId();
		Session session = this.repository.getSession(toSaveId);

		assertThat(session.getId()).isEqualTo(toSaveId);
		
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
				.isEqualTo(toSave.getAttribute(expectedAttributeName));

		Optional<String> actualAttributeValue = session.getAttribute(expectedAttributeName);
		Assert.assertEquals(expectedAttributeValue, actualAttributeValue.get());

		// Update a property
		toSave.setAttribute(expectedAttributeName, "c");
		this.repository.save(toSave);
		toSaveId = toSave.getId();
		
		session = this.repository.getSession(toSaveId);

		assertThat(session.getId()).isEqualTo(toSaveId);
		
		
		Object principal = context.getAuthentication().getPrincipal();
		String principalString = principal.toString();
		Map<String, OgmSessionRepository.OgmSession> sessions = this.repository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principalString);		
		Assert.assertEquals(1,  sessions.size());
		
		this.repository.delete(toSaveId);

		assertThat(this.repository.getSession(toSaveId)).isNull();

	}
	
}
