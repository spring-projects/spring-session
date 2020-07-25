/*
 * Copyright 2014-2020 the original author or authors.
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

package org.springframework.session.web.http;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.OncePerRequestFilterAopTests.Config;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringJUnitConfig(classes = Config.class)
class OncePerRequestFilterAopTests {

	@Test
	void doFilterOnce(@Autowired final OncePerRequestFilter filter) {
		assertThatCode(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				new MockFilterChain())).as("`doFilter` does not throw NPE with the bean is being proxied by Spring AOP")
						.doesNotThrowAnyException();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Configuration
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	@Aspect
	public static class Config {

		@Bean
		public SessionRepository sessionRepository() {
			return Mockito.mock(SessionRepository.class);
		}

		@Bean
		public SessionRepositoryFilter filter() {
			return new SessionRepositoryFilter(sessionRepository());
		}

		@AfterReturning("execution(* SessionRepositoryFilter.doFilterInternal(..))")
		public void doInternalFilterPointcut() {
			// no op
		}

	}

}
