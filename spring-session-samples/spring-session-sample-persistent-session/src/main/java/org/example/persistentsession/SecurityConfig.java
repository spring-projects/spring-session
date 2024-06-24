package org.example.persistentsession;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// @formatter:off
		http
				.csrf((csrf) -> csrf
						.csrfTokenRepository(new CookieCsrfTokenRepository())
				)
				.authorizeHttpRequests((authorize) -> authorize
						.anyRequest().authenticated()
				)
				.formLogin(Customizer.withDefaults())
				.logout(Customizer.withDefaults());
		return http.build();
		// @formatter:on
	}

	@Bean
	public UserDetailsService userDetailsService() {
		UserDetails user = User.withDefaultPasswordEncoder()
			.username("user")
			.password("password")
			.roles("USER")
			.build();
		UserDetails user2 = User.withDefaultPasswordEncoder()
			.username("user2")
			.password("password")
			.roles("USER")
			.build();
		UserDetails admin = User.withDefaultPasswordEncoder()
			.username("admin")
			.password("password")
			.roles("ADMIN")
			.build();
		return new InMemoryUserDetailsManager(user, user2, admin);
	}

}
