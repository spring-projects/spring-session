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

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.ogm.session.SessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.neo4j.config.annotation.web.http.EnableOgmHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Integration tests for {@link JdbcOperationsSessionRepository} using HSQLDB database.
 *
 * @author Vedran Pavic
 */
@WebAppConfiguration
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class HsqldbJdbcOperationsSessionRepositoryITests {

	private SecurityContext context;

	private SecurityContext changedContext;
	
	@Before
	public void setup() throws Exception {
		this.context = SecurityContextHolder.createEmptyContext();
		this.context.setAuthentication(
				new UsernamePasswordAuthenticationToken("username-" + UUID.randomUUID(),
						"na", AuthorityUtils.createAuthorityList("ROLE_USER")));

		this.changedContext = SecurityContextHolder.createEmptyContext();
		this.changedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
				"changedContext-" + UUID.randomUUID(), "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));
	}
	
	@EnableOgmHttpSession
	protected static class BaseConfig {

		@Bean
		public PlatformTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}
		
		@Bean
		public SessionFactory sessionFactory() {		
			SessionFactory sessionFactory = new SessionFactory("school.domain");
			return sessionFactory;
		}

	}
	
	@Configuration
	static class Config extends BaseConfig {

		@Bean
		public EmbeddedDatabase dataSource() {
			return new EmbeddedDatabaseBuilder()
					.setType(EmbeddedDatabaseType.HSQL)
					.addScript("org/springframework/session/jdbc/schema-hsqldb.sql")
					.build();
		}

//		@Value("${neo4.connectionPoolSize:15}")
//		protected int neo4jConnectionPoolSize;
//		
//		@Value("${spring.data.neo4j.uri:bolt://neo4j:dev@localhost}")
//		protected String neo4jUri;
//		
//		@Value("${neo4.driver:org.neo4j.ogm.drivers.bolt.driver.BoltDriver}")
//		protected String neo4jDriver;
//		
//		@Bean
//		public org.neo4j.ogm.config.Configuration configureOgm() {
//
////			Configuration configuration = new Configuration();
//	//
////		    configuration.driverConfiguration()
////		        .setDriverClassName(neo4jDriver)
////		        .setURI(neo4jUri)
////		        .setConnectionPoolSize(neo4jConnectionPoolSize);
//
//	//// Note - OGM 3.0 Approach:	    
//			org.neo4j.ogm.config.Configuration configuration = new org.neo4j.ogm.config.Configuration.Builder().uri(neo4jUri).build();
//			Credentials credentials = configuration.getCredentials();
//			
//	        return configuration;
//	        
//		}
//		
//		@Bean
//		public SessionFactory sessionFactory() {		
//			SessionFactory sessionFactory = new SessionFactory("school.domain");
//			return sessionFactory;
//		}

		
	}

	@Test
	public void a() {
		
	}
	
	
	
}
