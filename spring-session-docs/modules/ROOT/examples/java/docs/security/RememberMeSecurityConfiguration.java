/*
 * Copyright 2014-2022 the original author or authors.
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

package docs.security;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;

/**
 * @author rwinch
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableSpringHttpSession
public class RememberMeSecurityConfiguration {

	// @formatter:off
	// tag::http-rememberme[]
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// ... additional configuration ...
			.rememberMe((rememberMe) -> rememberMe
				.rememberMeServices(rememberMeServices())
			);
		// end::http-rememberme[]

		return http
			.formLogin(Customizer.withDefaults())
			.authorizeHttpRequests((authorize) -> authorize
				.anyRequest().authenticated()
			).build();
	}

	// tag::rememberme-bean[]
	@Bean
	public SpringSessionRememberMeServices rememberMeServices() {
		SpringSessionRememberMeServices rememberMeServices =
				new SpringSessionRememberMeServices();
		// optionally customize
		rememberMeServices.setAlwaysRemember(true);
		return rememberMeServices;
	}
	// end::rememberme-bean[]
	// @formatter:on

	@Bean
	public InMemoryUserDetailsManager userDetailsService() {
		return new InMemoryUserDetailsManager(
				User.withUsername("user").password("{noop}password").roles("USER").build());
	}

	@Bean
	MapSessionRepository sessionRepository() {
		return new MapSessionRepository(new ConcurrentHashMap<>());
	}

}
// end::class[]
