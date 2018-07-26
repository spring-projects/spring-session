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

package sample;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;
import org.testcontainers.containers.GenericContainer;
import sample.pages.HomePage;
import sample.pages.LoginPage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.webdriver.MockMvcHtmlUnitDriverBuilder;

/**
 * @author Eddú Meléndez
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
public class BootTests {

	private static final String DOCKER_IMAGE = "redis:4.0.10";

	@Autowired
	private MockMvc mockMvc;

	private WebDriver driver;

	@Before
	public void setup() {
		this.driver = MockMvcHtmlUnitDriverBuilder.mockMvcSetup(this.mockMvc).build();
	}

	@After
	public void tearDown() {
		this.driver.quit();
	}

	@Test
	public void home() {
		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
	}

	@Test
	public void login() {
		LoginPage login = HomePage.go(this.driver);
		HomePage home = login.form().login(HomePage.class);
		home.assertAt();
		home.containCookie("SESSION");
		home.doesNotContainCookie("JSESSIONID");
	}

	@Test
	public void logout() {
		LoginPage login = HomePage.go(this.driver);
		HomePage home = login.form().login(HomePage.class);
		home.logout();
		login.assertAt();
	}

	@TestConfiguration
	static class Config {

		@Bean
		public GenericContainer redisContainer() {
			GenericContainer redisContainer = new GenericContainer(DOCKER_IMAGE)
					.withExposedPorts(6379);
			redisContainer.start();
			return redisContainer;
		}

		@Bean
		public LettuceConnectionFactory redisConnectionFactory() {
			return new LettuceConnectionFactory(redisContainer().getContainerIpAddress(),
					redisContainer().getFirstMappedPort());
		}

		@Bean
		public FilterRegistrationBean<SetCookieHandlerFilter> testFilter() {
			FilterRegistrationBean<SetCookieHandlerFilter> registrationBean = new FilterRegistrationBean<>(
					new SetCookieHandlerFilter());
			registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
			return registrationBean;
		}

	}

	private static class SetCookieHandlerFilter implements Filter {

		@Override
		public void init(FilterConfig filterConfig) {
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse response,
				FilterChain chain) throws IOException, ServletException {
			final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
			HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(
					httpServletResponse) {

				@Override
				public void addHeader(String name, String value) {
					if (HttpHeaders.SET_COOKIE.equals(name)) {
						List<HttpCookie> cookies = HttpCookie.parse(value);
						if (!cookies.isEmpty()) {
							addCookie(toServletCookie(cookies.get(0)));
						}
					}
					super.setHeader(name, value);
				}

			};

			chain.doFilter(request, responseWrapper);
		}

		@Override
		public void destroy() {
		}

		private static Cookie toServletCookie(HttpCookie httpCookie) {
			Cookie cookie = new Cookie(httpCookie.getName(), httpCookie.getValue());
			String domain = httpCookie.getDomain();
			if (domain != null) {
				cookie.setDomain(domain);
			}
			cookie.setMaxAge((int) httpCookie.getMaxAge());
			cookie.setPath(httpCookie.getPath());
			cookie.setSecure(httpCookie.getSecure());
			cookie.setHttpOnly(httpCookie.isHttpOnly());
			return cookie;
		}

	}

}
