/*
 * Copyright 2002-2016 the original author or authors.
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

import com.mongodb.DBObject;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jakub Kubrynski
 */
public class StandardJdkSerializerTest {

	@Test
	public void shouldExtractPrincipalNameFromAttributes() throws Exception {
		//given
		StandardJdkSerializer sut = new StandardJdkSerializer();
		MapSession toSerialize = new MapSession();
		String principalName = "john_the_springer";
		toSerialize.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);

		//when
		DBObject dbObject = sut.serializeSession(toSerialize);

		//then
		assertThat(dbObject.get(sut.getPrincipalFieldName())).isEqualTo(principalName);
	}

	@Test
	public void shouldExtractPrincipalNameFromAuthentication() throws Exception {
		//given
		StandardJdkSerializer sut = new StandardJdkSerializer();
		MapSession toSerialize = new MapSession();
		String principalName = "john_the_springer";
		SecurityContextImpl context = new SecurityContextImpl();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(principalName, null));
		toSerialize.setAttribute("SPRING_SECURITY_CONTEXT", context);

		//when
		DBObject dbObject = sut.serializeSession(toSerialize);

		//then
		assertThat(dbObject.get(sut.getPrincipalFieldName())).isEqualTo(principalName);
	}

	@Test
	public void verifyRoundTripSerialization() throws Exception {
		//given
		StandardJdkSerializer sut = new StandardJdkSerializer();
		MapSession toSerialize = new MapSession();
		toSerialize.setAttribute("username", "john_the_springer");

		//when
		DBObject dbObject = sut.serializeSession(toSerialize);
		ExpiringSession deserialized = sut.deserializeSession(dbObject);

		//then
		assertThat(deserialized).isEqualToComparingFieldByField(toSerialize);
	}
}