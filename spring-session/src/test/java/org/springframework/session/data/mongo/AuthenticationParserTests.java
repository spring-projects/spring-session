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
package org.springframework.session.data.mongo;

import org.junit.Test;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jakub Kubrynski
 */
public class AuthenticationParserTests {

	@Test
	public void shouldExtractName() {
		// given
		String principalName = "john_the_springer";
		SecurityContextImpl context = new SecurityContextImpl();
		context.setAuthentication(
				new UsernamePasswordAuthenticationToken(principalName, null));

		// when
		String extractedName = AuthenticationParser.extractName(context);

		// then
		assertThat(extractedName).isEqualTo(principalName);
	}

}
