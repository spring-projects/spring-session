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

package com.example;

import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.security.reactive.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.ReactiveSessionRegistry;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.PreventLoginServerMaximumSessionsExceededHandler;
import org.springframework.security.web.server.authentication.SessionLimit;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.session.security.SpringSessionBackedReactiveSessionRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
		// @formatter:off
		return http
			.authorizeExchange(exchanges -> exchanges
					.matchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
					.anyExchange().authenticated())
			.formLogin(Customizer.withDefaults())
			.sessionManagement((sessions) -> sessions
					.concurrentSessions((concurrency) -> concurrency
							.maximumSessions((authentication) -> {
								if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_UNLIMITED_SESSIONS"))) {
									return Mono.empty();
								}
								return Mono.just(1);
							})
							.maximumSessionsExceededHandler(new PreventLoginServerMaximumSessionsExceededHandler())
					)
			)
			.build();
		// @formatter:on
	}

	@Bean
	<S extends Session> SpringSessionBackedReactiveSessionRegistry<S> sessionRegistry(
			ReactiveSessionRepository<S> sessionRepository,
			ReactiveFindByIndexNameSessionRepository<S> indexedSessionRepository) {
		return new SpringSessionBackedReactiveSessionRegistry<>(sessionRepository, indexedSessionRepository);
	}

	@Bean
	MapReactiveUserDetailsService reactiveUserDetailsService() {
		UserDetails user = User.withDefaultPasswordEncoder()
			.username("user")
			.password("password")
			.roles("USER")
			.build();
		UserDetails unlimited = User.withDefaultPasswordEncoder()
			.username("unlimited")
			.password("password")
			.roles("USER", "UNLIMITED_SESSIONS")
			.build();
		return new MapReactiveUserDetailsService(user, unlimited);
	}

}
