/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package sample.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import sample.session.CompositeAuthenticationSuccessHandler;
import sample.session.SpringSessionPrincipalNameSuccessHandler;

/**
 * @author Rob Winch
 */

@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	// tag::config[]
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		CompositeAuthenticationSuccessHandler successHandler = createHandler();

		http
			.formLogin()
				.successHandler(successHandler)
				.loginPage("/login")
				.permitAll()
				.and()
			.authorizeRequests()
				.antMatchers("/resources/**").permitAll()
				.anyRequest().authenticated()
				.and()
			.logout()
				.permitAll();
	}
	// end::config[]

	// tag::handler[]
	private CompositeAuthenticationSuccessHandler createHandler() {
		SpringSessionPrincipalNameSuccessHandler setUsernameHandler =
				new SpringSessionPrincipalNameSuccessHandler();
		SavedRequestAwareAuthenticationSuccessHandler defaultHandler =
				new SavedRequestAwareAuthenticationSuccessHandler();

		CompositeAuthenticationSuccessHandler successHandler =
				new CompositeAuthenticationSuccessHandler(setUsernameHandler, defaultHandler);
		return successHandler;
	}
	// end::handler[]

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		auth
			.inMemoryAuthentication()
				.withUser("user").password("password").roles("USER");
	}
}
