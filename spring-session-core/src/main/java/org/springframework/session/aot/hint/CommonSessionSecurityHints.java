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

package org.springframework.session.aot.hint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * A {@link RuntimeHintsRegistrar} for common session security hints.
 *
 * @author Marcus Da Coregio
 */
public class CommonSessionSecurityHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		Arrays.asList(TypeReference.of(String.class), TypeReference.of(ArrayList.class),
				TypeReference.of(TreeSet.class), TypeReference.of(SecurityContextImpl.class),
				TypeReference.of(SimpleGrantedAuthority.class), TypeReference.of(User.class),
				TypeReference.of(Number.class), TypeReference.of(Long.class), TypeReference.of(Integer.class),
				TypeReference.of(AbstractAuthenticationToken.class),
				TypeReference.of(UsernamePasswordAuthenticationToken.class), TypeReference.of(StackTraceElement.class),
				TypeReference.of(Throwable.class), TypeReference.of(Exception.class),
				TypeReference.of(RuntimeException.class), TypeReference.of(AuthenticationException.class),
				TypeReference.of(BadCredentialsException.class), TypeReference.of(UsernameNotFoundException.class),
				TypeReference.of(AccountExpiredException.class), TypeReference.of(ProviderNotFoundException.class),
				TypeReference.of(DisabledException.class), TypeReference.of(LockedException.class),
				TypeReference.of(AuthenticationServiceException.class),
				TypeReference.of(CredentialsExpiredException.class),
				TypeReference.of(InsufficientAuthenticationException.class),
				TypeReference
						.of("org.springframework.security.web.authentication.session.SessionAuthenticationException"),
				TypeReference.of(
						"org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException"),
				TypeReference.of("java.util.Collections$UnmodifiableCollection"),
				TypeReference.of("java.util.Collections$UnmodifiableList"),
				TypeReference.of("java.util.Collections$EmptyList"),
				TypeReference.of("java.util.Collections$UnmodifiableRandomAccessList"),
				TypeReference.of("java.util.Collections$UnmodifiableSet"),
				TypeReference.of("org.springframework.security.core.userdetails.User$AuthorityComparator"))
				.forEach(hints.serialization()::registerType);
		registerOAuth2ClientHintsIfNeeded(hints);
		registerOAuth2ResourceServerHintsIfNeeded(hints);
	}

	private void registerOAuth2ResourceServerHintsIfNeeded(RuntimeHints hints) {
		Arrays.asList(
				TypeReference.of("org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken"),
				TypeReference.of(
						"org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken"),
				TypeReference.of("org.springframework.security.oauth2.core.OAuth2AuthenticationException"))
				.forEach((type) -> hints.serialization().registerType(type, (hint) -> hint.onReachableType(TypeReference
						.of("org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken"))));
	}

	private void registerOAuth2ClientHintsIfNeeded(RuntimeHints hints) {
		Arrays.asList(
				TypeReference.of("org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken"),
				TypeReference
						.of("org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken"),
				TypeReference.of(
						"org.springframework.security.oauth2.client.authentication.OAuth2AuthorizationCodeAuthenticationToken"),
				TypeReference.of("org.springframework.security.oauth2.core.OAuth2AuthenticationException"))
				.forEach((type) -> hints.serialization().registerType(type, (hint) -> hint.onReachableType(TypeReference
						.of("org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken"))));
	}

}
