/*
 * Copyright 2014-2017 the original author or authors.
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
package org.springframework.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.server.session.SpringSessionWebSessionManager;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Wire up a {@link WebSessionManager} using a Reactive {@link ReactorSessionRepository} from the application context.
 *
 * @author Greg Turnquist
 * @since 2.0
 *
 * @see EnableSpringWebSession
 */
@Configuration
public class SpringWebSessionConfiguration {

	/**
	 * Configure a {@link WebSessionManager} using a provided {@link ReactorSessionRepository}.
	 *
	 * @param repository - a bean that implements {@link ReactorSessionRepository}.
	 * @return a configured {@link WebSessionManager} registered with a preconfigured name.
	 */
	@Bean(WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME)
	public WebSessionManager webSessionManager(ReactorSessionRepository<?> repository) {
		return new SpringSessionWebSessionManager(repository);
	}
}
