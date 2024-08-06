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

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.session.PersistentSessionPrincipalNameResolver;
import org.springframework.session.PersistentSessionPrincipalRestorer;
import org.springframework.session.PersistentSessionRepository;
import org.springframework.session.SecurityContextAttributePersistentSessionRestorer;
import org.springframework.session.SecurityContextAttributePrincipalNameResolver;
import org.springframework.session.config.SessionRepositoryCustomizer;

@Configuration(proxyBeanMethods = false)
@Import(SpringHttpSessionConfiguration.class)
public class PersistentSessionConfiguration implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	private SecretKey secretKey;

	@Bean
	PersistentSessionRepository persistentSessionRepository(
			ObjectProvider<PersistentSessionPrincipalNameResolver> principalNameResolverProvider,
			ObjectProvider<PersistentSessionPrincipalRestorer> principalRestorerProvider,
			ObjectProvider<SessionRepositoryCustomizer<PersistentSessionRepository>> sessionRepositoryCustomizers) {
		PersistentSessionPrincipalNameResolver principalResolver = principalNameResolverProvider
			.getIfAvailable(this::getDefaultPrincipalResolver);
		PersistentSessionPrincipalRestorer principalRestorer = principalRestorerProvider
			.getIfAvailable(this::getDefaultPrincipalRestorer);
		PersistentSessionRepository repository = new PersistentSessionRepository(getSecretKey(), principalResolver,
				principalRestorer);
		sessionRepositoryCustomizers.orderedStream().forEach((customizer) -> customizer.customize(repository));
		return repository;
	}

	@Autowired(required = false)
	@Qualifier("springSessionPersistentSessionSecretKey")
	public void setSecretKey(SecretKey secretKey) {
		this.secretKey = secretKey;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	private SecretKey getSecretKey() {
		if (this.secretKey != null) {
			return this.secretKey;
		}
		try {
			return KeyGenerator.getInstance("AES").generateKey();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	private PersistentSessionPrincipalNameResolver getDefaultPrincipalResolver() {
		return new SecurityContextAttributePrincipalNameResolver();
	}

	private PersistentSessionPrincipalRestorer getDefaultPrincipalRestorer() {
		String[] beanNamesForType = this.applicationContext.getBeanNamesForType(UserDetailsService.class);
		if (beanNamesForType.length != 1) {
			throw new IllegalStateException(
					"Could not find a UserDetailsService bean to construct a PersistentSessionPrincipalRestorer, please provide a PersistentSessionPrincipalRestorer bean");
		}
		UserDetailsService userDetailsService = (UserDetailsService) this.applicationContext
			.getBean(beanNamesForType[0]);
		return new SecurityContextAttributePersistentSessionRestorer(userDetailsService);
	}

}
