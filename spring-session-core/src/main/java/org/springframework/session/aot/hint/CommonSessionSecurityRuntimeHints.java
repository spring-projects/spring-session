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

import java.util.Arrays;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * A {@link RuntimeHintsRegistrar} for common session security hints.
 *
 * @author Marcus Da Coregio
 */
class CommonSessionSecurityRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		registerSecurityHintsIfNeeded(hints);
		registerOAuth2ClientHintsIfNeeded(hints);
		registerOAuth2ResourceServerHintsIfNeeded(hints);
	}

	private void registerSecurityHintsIfNeeded(RuntimeHints hints) {
		Arrays.asList(TypeReference.of("org.springframework.security.core.context.SecurityContextImpl"),
				TypeReference.of("org.springframework.security.core.authority.SimpleGrantedAuthority"),
				TypeReference.of("org.springframework.security.core.userdetails.User"),
				TypeReference.of("org.springframework.security.authentication.AbstractAuthenticationToken"),
				TypeReference.of("org.springframework.security.authentication.UsernamePasswordAuthenticationToken"),
				TypeReference.of("org.springframework.security.core.AuthenticationException"),
				TypeReference.of("org.springframework.security.authentication.BadCredentialsException"),
				TypeReference.of("org.springframework.security.core.userdetails.UsernameNotFoundException"),
				TypeReference.of("org.springframework.security.authentication.AccountExpiredException"),
				TypeReference.of("org.springframework.security.authentication.ProviderNotFoundException"),
				TypeReference.of("org.springframework.security.authentication.DisabledException"),
				TypeReference.of("org.springframework.security.authentication.LockedException"),
				TypeReference.of("org.springframework.security.authentication.AuthenticationServiceException"),
				TypeReference.of("org.springframework.security.authentication.CredentialsExpiredException"),
				TypeReference.of("org.springframework.security.authentication.InsufficientAuthenticationException"),
				TypeReference
					.of("org.springframework.security.web.authentication.session.SessionAuthenticationException"),
				TypeReference
					.of("org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException"),
				TypeReference.of("org.springframework.security.core.userdetails.User$AuthorityComparator"))
			.forEach((type) -> hints.serialization()
				.registerType(type, (hint) -> hint.onReachableType(
						TypeReference.of("org.springframework.security.core.context.SecurityContextImpl"))));
	}

	private void registerOAuth2ResourceServerHintsIfNeeded(RuntimeHints hints) {
		Arrays.asList(
				TypeReference.of("org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken"),
				TypeReference
					.of("org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken"),
				TypeReference.of("org.springframework.security.oauth2.core.OAuth2AuthenticationException"))
			.forEach((type) -> hints.serialization()
				.registerType(type, (hint) -> hint.onReachableType(TypeReference
					.of("org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken"))));
	}

	private void registerOAuth2ClientHintsIfNeeded(RuntimeHints hints) {
		Arrays.asList(
				TypeReference.of("org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken"),
				TypeReference
					.of("org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken"),
				TypeReference
					.of("org.springframework.security.oauth2.client.authentication.OAuth2AuthorizationCodeAuthenticationToken"),
				TypeReference.of("org.springframework.security.oauth2.core.OAuth2AuthenticationException"))
			.forEach((type) -> hints.serialization()
				.registerType(type, (hint) -> hint.onReachableType(TypeReference
					.of("org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken"))));
	}

}
