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
package org.springframework.session.config.annotation.web.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.web.server.session.SpringSessionWebSessionStore;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionIdResolver;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Wire up a {@link WebSessionManager} using a Reactive {@link ReactiveSessionRepository} from the application context.
 *
 * @author Greg Turnquist
 * @author Rob Winch
 * @since 2.0
 *
 * @see EnableSpringWebSession
 */
@Configuration
public class SpringWebSessionConfiguration {

	/**
	 * Optional override of default {@link WebSessionIdResolver}.
	 */
	@Autowired(required = false)
	private WebSessionIdResolver webSessionIdResolver;

	/**
	 * Configure a {@link WebSessionManager} using a provided {@link ReactiveSessionRepository}.
	 *
	 * @param repository a bean that implements {@link ReactiveSessionRepository}.
	 * @return a configured {@link WebSessionManager} registered with a preconfigured name.
	 */
	@Bean(WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME)
	public WebSessionManager webSessionManager(ReactiveSessionRepository<? extends Session> repository) {
		SpringSessionWebSessionStore<? extends Session> sessionStore = new SpringSessionWebSessionStore<>(repository);
		DefaultWebSessionManager manager = new DefaultWebSessionManager();
		manager.setSessionStore(sessionStore);

		if (this.webSessionIdResolver != null) {
			manager.setSessionIdResolver(this.webSessionIdResolver);
		}

		return manager;
	}
}
