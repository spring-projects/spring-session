/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session.web.http;

import static org.assertj.core.api.Assertions.*;

import javax.servlet.http.Cookie;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

/**
 * @author Rob Winch
 *
 */
public class DefaultCookieSerializerTests {

	String cookieName;

	MockHttpServletRequest request;

	MockHttpServletResponse response;

	DefaultCookieSerializer serializer;

	String sessionId;

	@Before
	public void setup() {
		cookieName = "SESSION";
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		sessionId = "sessionId";
		serializer = new DefaultCookieSerializer();
	}

	// --- readCookieValues ---

	@Test
	public void readCookieValuesNull() {
		assertThat(serializer.readCookieValues(request)).isEmpty();
	}

	@Test
	public void readCookieValuesSingle() {
		request.setCookies(new Cookie(cookieName, sessionId));

		assertThat(serializer.readCookieValues(request)).containsOnly(sessionId);
	}

	@Test
	public void readCookieValuesSingleAndInvalidName() {
		request.setCookies(new Cookie(cookieName, sessionId), new Cookie(cookieName+"INVALID", sessionId + "INVALID"));

		assertThat(serializer.readCookieValues(request)).containsOnly(sessionId);
	}

	@Test
	public void readCookieValuesMulti() {
		String secondSession = "secondSessionId";
		request.setCookies(new Cookie(cookieName, sessionId), new Cookie(cookieName, secondSession));

		assertThat(serializer.readCookieValues(request)).containsExactly(sessionId, secondSession);
	}

	@Test
	public void readCookieValuesMultiCustomSessionCookieName() {
		setCookieName("JSESSIONID");
		String secondSession = "secondSessionId";
		request.setCookies(new Cookie(cookieName, sessionId), new Cookie(cookieName, secondSession));

		assertThat(serializer.readCookieValues(request)).containsExactly(sessionId, secondSession);
	}

	// --- writeCookie ---

	@Test
	public void writeCookie() {
		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getValue()).isEqualTo(sessionId);
	}

	// --- httpOnly ---

	@Test
	public void writeCookieHttpOnlyDefault() {
		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().isHttpOnly()).isTrue();
	}

	@Test
	public void writeCookieHttpOnlySetTrue() {
		serializer.setUseHttpOnlyCookie(true);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().isHttpOnly()).isTrue();
	}

	@Test
	public void writeCookieHttpOnlySetFalse() {
		serializer.setUseHttpOnlyCookie(false);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().isHttpOnly()).isFalse();
	}

	// --- domainName ---

	@Test
	public void writeCookieDomainNameDefault() {
		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getDomain()).isNull();
	}

	@Test
	public void writeCookieDomainNameCustom() {
		String domainName = "example.com";
		serializer.setDomainName(domainName);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getDomain()).isEqualTo(domainName);
	}

	@Test(expected=IllegalStateException.class)
	public void setDomainNameAndDomainNamePatternThrows() {
		serializer.setDomainName("example.com");
		serializer.setDomainNamePattern(".*");
	}

	// --- domainNamePattern ---

	@Test
	public void writeCookieDomainNamePattern() {
		String domainNamePattern = "^.+?\\.(\\w+\\.[a-z]+)$";
		serializer.setDomainNamePattern(domainNamePattern);

		String[] matchingDomains = {"child.sub.example.com","www.example.com"};
		for(String domain : matchingDomains) {
			request.setServerName(domain);
			serializer.writeCookieValue(cookieValue(sessionId));
			assertThat(getCookie().getDomain()).isEqualTo("example.com");

			response = new MockHttpServletResponse();
		}

		String[] notMatchingDomains = {"example.com", "localhost","127.0.0.1"};
		for(String domain : notMatchingDomains) {
			request.setServerName(domain);
			serializer.writeCookieValue(cookieValue(sessionId));
			assertThat(getCookie().getDomain()).isNull();

			response = new MockHttpServletResponse();
		}
	}

	@Test(expected=IllegalStateException.class)
	public void setDomainNamePatternAndDomainNameThrows() {
		serializer.setDomainNamePattern(".*");
		serializer.setDomainName("example.com");
	}

	// --- cookieName ---

	@Test
	public void writeCookieCookieNameDefault() {
		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getName()).isEqualTo("SESSION");
	}

	@Test
	public void writeCookieCookieNameCustom() {
		String cookieName = "JSESSIONID";
		setCookieName(cookieName);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getName()).isEqualTo(cookieName);
	}

	@Test(expected=IllegalArgumentException.class)
	public void setCookieNameNullThrows() {
		serializer.setCookieName(null);
	}

	// --- cookiePath ---

	@Test
	public void writeCookieCookiePathDefaultEmptyContextPathUsed() {
		request.setContextPath("");

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/");
	}

	@Test
	public void writeCookieCookiePathDefaultContextPathUsed() {
		request.setContextPath("/context");

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/context/");
	}

	@Test
	public void writeCookieCookiePathExplicitNullCookiePathContextPathUsed() {
		request.setContextPath("/context");
		serializer.setCookiePath(null);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/context/");
	}

	@Test
	public void writeCookieCookiePathExplicitCookiePath() {
		request.setContextPath("/context");
		serializer.setCookiePath("/");

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/");
	}

	// --- cookieMaxAge ---

	@Test
	public void writeCookieCookieMaxAgeDefault() {
		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getMaxAge()).isEqualTo(-1);
	}

	@Test
	public void writeCookieCookieMaxAgeExplicit() {
		serializer.setCookieMaxAge(100);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getMaxAge()).isEqualTo(100);
	}

	@Test
	public void writeCookieCookieMaxAgeExplicitEmptyCookie() {
		serializer.setCookieMaxAge(100);

		serializer.writeCookieValue(cookieValue(""));

		assertThat(getCookie().getMaxAge()).isEqualTo(0);
	}

	// --- secure ---

	@Test
	public void writeCookieDefaultInsecureRequest() {
		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getSecure()).isFalse();
	}

	@Test
	public void writeCookieSecureSecureRequest() {
		request.setSecure(true);
		serializer.setUseSecureCookie(true);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getSecure()).isTrue();
	}

	@Test
	public void writeCookieSecureInsecureRequest() {
		serializer.setUseSecureCookie(true);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getSecure()).isTrue();
	}

	@Test
	public void writeCookieInsecureSecureRequest() {
		request.setSecure(true);
		serializer.setUseSecureCookie(false);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getSecure()).isFalse();
	}

	@Test
	public void writeCookieInecureInsecureRequest() {
		serializer.setUseSecureCookie(false);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getSecure()).isFalse();
	}

	// --- jvmRoute ---

	@Test
	public void writeCookieJvmRoute() {
		String jvmRoute = "route";
		serializer.setJvmRoute(jvmRoute);

		serializer.writeCookieValue(cookieValue(sessionId));

		assertThat(getCookie().getValue()).isEqualTo(sessionId + "." + jvmRoute);
	}

	@Test
	public void readCookieJvmRoute() {
		String jvmRoute = "route";
		serializer.setJvmRoute(jvmRoute);
		request.setCookies(new Cookie(cookieName, sessionId + "." + jvmRoute));

		assertThat(serializer.readCookieValues(request)).containsOnly(sessionId);
	}

	@Test
	public void readCookieJvmRouteRouteMissing() {
		String jvmRoute = "route";
		serializer.setJvmRoute(jvmRoute);
		request.setCookies(new Cookie(cookieName, sessionId));

		assertThat(serializer.readCookieValues(request)).containsOnly(sessionId);
	}

	@Test
	public void readCookieJvmRouteOnlyRoute() {
		String jvmRoute = "route";
		serializer.setJvmRoute(jvmRoute);
		request.setCookies(new Cookie(cookieName, "." + jvmRoute));

		assertThat(serializer.readCookieValues(request)).containsOnly("");
	}

	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
		this.serializer.setCookieName(cookieName);
	}

	private Cookie getCookie() {
		return response.getCookie(cookieName);
	}

	private CookieValue cookieValue(String cookieValue) {
		return new CookieValue(request, response, cookieValue);
	}
}
