/*
 * Copyright 2014-present the original author or authors.
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

package sample;

import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestContainersConfig.class)
class JdbcJsonAttributeTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	ObjectMapper objectMapperWithModules;

	@Autowired
	JdbcClient jdbcClient;

	String username;

	@Autowired
	void setSecurityProperties(SecurityProperties securityProperties) {
		this.username = securityProperties.getUser().getName();
	}

	@BeforeEach
	void setup() {
		ObjectMapper copy = this.objectMapper.copy();
		copy.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
		this.objectMapperWithModules = copy;
		this.jdbcClient.sql("DELETE FROM spring_session_attributes").update();
		this.jdbcClient.sql("DELETE FROM spring_session").update();
	}

	@Test
	void loginShouldSaveSecurityContextAsJson() throws Exception {
		Cookie sessionCookie = this.mvc.perform(formLogin().user(this.username).password("password"))
			.andExpect(authenticated())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
		String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()));
		Object attributeBytes = this.jdbcClient.sql("""
				SELECT attribute_bytes::text FROM spring_session_attributes
				INNER JOIN spring_session s ON s.primary_id = session_primary_id
				WHERE attribute_name = 'SPRING_SECURITY_CONTEXT'
				AND s.session_id = :id
				""").param("id", sessionId).query().singleValue();
		SecurityContext securityContext = this.objectMapperWithModules.readValue((String) attributeBytes,
				SecurityContext.class);
		assertThat(securityContext).isNotNull();
		assertThat(securityContext.getAuthentication().getName()).isEqualTo(this.username);
	}

	@Test
	void loginWhenQueryUsingJsonbOperatorThenReturns() throws Exception {
		this.mvc.perform(formLogin().user(this.username).password("password")).andExpect(authenticated());
		Object attributeBytes = this.jdbcClient.sql("""
				SELECT attribute_bytes::text FROM spring_session_attributes
				WHERE attribute_bytes -> 'authentication' -> 'principal' ->> 'username' = '%s'
				""".formatted(this.username)).query().singleValue();
		SecurityContext securityContext = this.objectMapperWithModules.readValue((String) attributeBytes,
				SecurityContext.class);
		assertThat(securityContext).isNotNull();
		assertThat(securityContext.getAuthentication().getName()).isEqualTo(this.username);
	}

}
