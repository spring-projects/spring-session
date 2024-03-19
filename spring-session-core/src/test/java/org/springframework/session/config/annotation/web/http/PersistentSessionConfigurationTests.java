/*
 * Copyright 2014-2024 the original author or authors.
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

package org.springframework.session.config.annotation.web.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.session.PersistentSessionPrincipalNameResolver;
import org.springframework.session.PersistentSessionPrincipalRestorer;
import org.springframework.session.PersistentSessionRepository;
import org.springframework.session.SecurityContextAttributePersistentSessionRestorer;
import org.springframework.session.SecurityContextAttributePrincipalNameResolver;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

/**
 * Tests for {@link PersistentSessionConfiguration}
 *
 * @author Marcus da Coregio
 */
@ExtendWith(SpringExtension.class)
class PersistentSessionConfigurationTests {

	AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

	PersistentSessionRepository repository;

	@Test
	void configureWhenNoUserDetailsServiceThenException() {
		assertThatException().isThrownBy(() -> registerConfig(EnablePersistentSessionConfig.class))
			.withRootCauseInstanceOf(IllegalStateException.class)
			.withMessageContaining(
					"Could not find a UserDetailsService bean to construct a PersistentSessionPrincipalRestorer, please provide a PersistentSessionPrincipalRestorer bean");
	}

	@Test
	void configureWhenDefaultsThenCreateExpectedDependencies() {
		registerConfig(UserDetailsServiceConfig.class, EnablePersistentSessionConfig.class);
		PersistentSessionPrincipalNameResolver resolver = (PersistentSessionPrincipalNameResolver) ReflectionTestUtils
			.getField(this.repository, "principalNameResolver");
		PersistentSessionPrincipalRestorer restorer = (PersistentSessionPrincipalRestorer) ReflectionTestUtils
			.getField(this.repository, "principalRestorer");
		assertThat(resolver).isInstanceOf(SecurityContextAttributePrincipalNameResolver.class);
		assertThat(restorer).isInstanceOf(SecurityContextAttributePersistentSessionRestorer.class);
	}

	private void registerConfig(Class<?>... clazz) {
		this.context.register(clazz);
		this.context.refresh();
		this.repository = this.context.getBean(PersistentSessionRepository.class);
	}

	@Configuration
	@EnablePersistentSession
	static class EnablePersistentSessionConfig {

	}

	@Configuration
	static class UserDetailsServiceConfig {

		@Bean
		public UserDetailsService userDetailsService() {
			UserDetails user = User.withDefaultPasswordEncoder()
				.username("user")
				.password("password")
				.roles("USER")
				.build();
			return new InMemoryUserDetailsManager(user);
		}

	}

}
