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

package org.springframework.session.web.http;

import java.util.Base64;

import javax.servlet.http.Cookie;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultCookieSerializer}.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @author Eddú Meléndez
 */
@RunWith(Parameterized.class)
public class DefaultCookieSerializerTests {

	@Parameters(name = "useBase64Encoding={0}")
	public static Object[] parameters() {
		return new Object[] { false, true };
	}

	private boolean useBase64Encoding;

	private String cookieName;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private DefaultCookieSerializer serializer;

	private String sessionId;

	public DefaultCookieSerializerTests(boolean useBase64Encoding) {
		this.useBase64Encoding = useBase64Encoding;
	}

	@Before
	public void setup() {
		this.cookieName = "SESSION";
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.sessionId = "sessionId";
		this.serializer = new DefaultCookieSerializer();
		this.serializer.setUseBase64Encoding(this.useBase64Encoding);
	}

	// --- readCookieValues ---

	@Test
	public void readCookieValuesNull() {
		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@Test
	public void readCookieValuesSingle() {
		this.request.setCookies(createCookie(this.cookieName, this.sessionId));

		assertThat(this.serializer.readCookieValues(this.request))
				.containsOnly(this.sessionId);
	}

	@Test
	public void readCookieSerializerUseBase64EncodingTrueValuesNotBase64() {
		this.sessionId = "&^%$*";
		this.serializer.setUseBase64Encoding(true);
		this.request.setCookies(new Cookie(this.cookieName, this.sessionId));

		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@Test
	public void readCookieValuesSingleAndInvalidName() {
		this.request.setCookies(createCookie(this.cookieName, this.sessionId),
				createCookie(this.cookieName + "INVALID", this.sessionId + "INVALID"));

		assertThat(this.serializer.readCookieValues(this.request))
				.containsOnly(this.sessionId);
	}

	@Test
	public void readCookieValuesMulti() {
		String secondSession = "secondSessionId";
		this.request.setCookies(createCookie(this.cookieName, this.sessionId),
				createCookie(this.cookieName, secondSession));

		assertThat(this.serializer.readCookieValues(this.request))
				.containsExactly(this.sessionId, secondSession);
	}

	@Test
	public void readCookieValuesMultiCustomSessionCookieName() {
		setCookieName("JSESSIONID");
		String secondSession = "secondSessionId";
		this.request.setCookies(createCookie(this.cookieName, this.sessionId),
				createCookie(this.cookieName, secondSession));

		assertThat(this.serializer.readCookieValues(this.request))
				.containsExactly(this.sessionId, secondSession);
	}

	// gh-392
	@Test
	public void readCookieValuesNullCookieValue() {
		this.request.setCookies(createCookie(this.cookieName, null));

		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@Test
	public void readCookieValuesNullCookieValueAndJvmRoute() {
		this.serializer.setJvmRoute("123");
		this.request.setCookies(createCookie(this.cookieName, null));

		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@Test
	public void readCookieValuesNullCookieValueAndNotNullCookie() {
		this.serializer.setJvmRoute("123");
		this.request.setCookies(createCookie(this.cookieName, null),
				createCookie(this.cookieName, this.sessionId));

		assertThat(this.serializer.readCookieValues(this.request))
				.containsOnly(this.sessionId);
	}

	// --- writeCookie ---

	@Test
	public void writeCookie() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookieValue()).isEqualTo(this.sessionId);
	}

	// --- httpOnly ---

	@Test
	public void writeCookieHttpOnlyDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().isHttpOnly()).isTrue();
	}

	@Test
	public void writeCookieHttpOnlySetTrue() {
		this.serializer.setUseHttpOnlyCookie(true);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().isHttpOnly()).isTrue();
	}

	@Test
	public void writeCookieHttpOnlySetFalse() {
		this.serializer.setUseHttpOnlyCookie(false);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().isHttpOnly()).isFalse();
	}

	// --- domainName ---

	@Test
	public void writeCookieDomainNameDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getDomain()).isNull();
	}

	@Test
	public void writeCookieDomainNameCustom() {
		String domainName = "example.com";
		this.serializer.setDomainName(domainName);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getDomain()).isEqualTo(domainName);
	}

	@Test
	public void setDomainNameAndDomainNamePatternThrows() {
		this.serializer.setDomainName("example.com");
		assertThatThrownBy(() -> this.serializer.setDomainNamePattern(".*"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Cannot set both domainName and domainNamePattern");
	}

	// --- domainNamePattern ---

	@Test
	public void writeCookieDomainNamePattern() {
		String domainNamePattern = "^.+?\\.(\\w+\\.[a-z]+)$";
		this.serializer.setDomainNamePattern(domainNamePattern);

		String[] matchingDomains = { "child.sub.example.com", "www.example.com" };
		for (String domain : matchingDomains) {
			this.request.setServerName(domain);
			this.serializer.writeCookieValue(cookieValue(this.sessionId));
			assertThat(getCookie().getDomain()).isEqualTo("example.com");

			this.response = new MockHttpServletResponse();
		}

		String[] notMatchingDomains = { "example.com", "localhost", "127.0.0.1" };
		for (String domain : notMatchingDomains) {
			this.request.setServerName(domain);
			this.serializer.writeCookieValue(cookieValue(this.sessionId));
			assertThat(getCookie().getDomain()).isNull();

			this.response = new MockHttpServletResponse();
		}
	}

	@Test
	public void setDomainNamePatternAndDomainNameThrows() {
		this.serializer.setDomainNamePattern(".*");
		assertThatThrownBy(() -> this.serializer.setDomainName("example.com"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Cannot set both domainName and domainNamePattern");
	}

	// --- cookieName ---

	@Test
	public void writeCookieCookieNameDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getName()).isEqualTo("SESSION");
	}

	@Test
	public void writeCookieCookieNameCustom() {
		String cookieName = "JSESSIONID";
		setCookieName(cookieName);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getName()).isEqualTo(cookieName);
	}

	@Test
	public void setCookieNameNullThrows() {
		assertThatThrownBy(() -> this.serializer.setCookieName(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("cookieName cannot be null");
	}

	// --- cookiePath ---

	@Test
	public void writeCookieCookiePathDefaultEmptyContextPathUsed() {
		this.request.setContextPath("");

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/");
	}

	@Test
	public void writeCookieCookiePathDefaultContextPathUsed() {
		this.request.setContextPath("/context");

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/context/");
	}

	@Test
	public void writeCookieCookiePathExplicitNullCookiePathContextPathUsed() {
		this.request.setContextPath("/context");
		this.serializer.setCookiePath(null);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/context/");
	}

	@Test
	public void writeCookieCookiePathExplicitCookiePath() {
		this.request.setContextPath("/context");
		this.serializer.setCookiePath("/");

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getPath()).isEqualTo("/");
	}

	// --- cookieMaxAge ---

	@Test
	public void writeCookieCookieMaxAgeDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getMaxAge()).isEqualTo(-1);
	}

	@Test
	public void writeCookieCookieMaxAgeExplicit() {
		this.serializer.setCookieMaxAge(100);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getMaxAge()).isEqualTo(100);
	}

	@Test
	public void writeCookieCookieMaxAgeExplicitEmptyCookie() {
		this.serializer.setCookieMaxAge(100);

		this.serializer.writeCookieValue(cookieValue(""));

		assertThat(getCookie().getMaxAge()).isEqualTo(0);
	}

	@Test
	public void writeCookieCookieMaxAgeExplicitCookieValue() {
		CookieValue cookieValue = cookieValue(this.sessionId);
		cookieValue.setCookieMaxAge(100);

		this.serializer.writeCookieValue(cookieValue);

		assertThat(getCookie().getMaxAge()).isEqualTo(100);
	}

	// --- secure ---

	@Test
	public void writeCookieDefaultInsecureRequest() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSecure()).isFalse();
	}

	@Test
	public void writeCookieSecureSecureRequest() {
		this.request.setSecure(true);
		this.serializer.setUseSecureCookie(true);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSecure()).isTrue();
	}

	@Test
	public void writeCookieSecureInsecureRequest() {
		this.serializer.setUseSecureCookie(true);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSecure()).isTrue();
	}

	@Test
	public void writeCookieInsecureSecureRequest() {
		this.request.setSecure(true);
		this.serializer.setUseSecureCookie(false);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSecure()).isFalse();
	}

	@Test
	public void writeCookieInecureInsecureRequest() {
		this.serializer.setUseSecureCookie(false);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSecure()).isFalse();
	}

	// --- jvmRoute ---

	@Test
	public void writeCookieJvmRoute() {
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);

		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookieValue()).isEqualTo(this.sessionId + "." + jvmRoute);
	}

	@Test
	public void readCookieJvmRoute() {
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);
		this.request.setCookies(
				createCookie(this.cookieName, this.sessionId + "." + jvmRoute));

		assertThat(this.serializer.readCookieValues(this.request))
				.containsOnly(this.sessionId);
	}

	@Test
	public void readCookieJvmRouteRouteMissing() {
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);
		this.request.setCookies(createCookie(this.cookieName, this.sessionId));

		assertThat(this.serializer.readCookieValues(this.request))
				.containsOnly(this.sessionId);
	}

	@Test
	public void readCookieJvmRouteOnlyRoute() {
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);
		this.request.setCookies(createCookie(this.cookieName, "." + jvmRoute));

		assertThat(this.serializer.readCookieValues(this.request)).containsOnly("");
	}

	// --- rememberMe ---

	@Test
	public void writeCookieRememberMeCookieMaxAgeDefault() {
		this.request.setAttribute("rememberMe", true);
		this.serializer.setRememberMeRequestAttribute("rememberMe");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getMaxAge()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	public void writeCookieRememberMeCookieMaxAgeOverride() {
		this.request.setAttribute("rememberMe", true);
		this.serializer.setRememberMeRequestAttribute("rememberMe");
		CookieValue cookieValue = cookieValue(this.sessionId);
		cookieValue.setCookieMaxAge(100);
		this.serializer.writeCookieValue(cookieValue);

		assertThat(getCookie().getMaxAge()).isEqualTo(100);
	}

	// --- sameSite ---

	@Test
	public void writeCookieDefaultSameSiteLax() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSameSite()).isEqualTo("Lax");
	}

	@Test
	public void writeCookieSetSameSiteLax() {
		this.serializer.setSameSite("Lax");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSameSite()).isEqualTo("Lax");
	}

	@Test
	public void writeCookieSetSameSiteStrict() {
		this.serializer.setSameSite("Strict");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSameSite()).isEqualTo("Strict");
	}

	@Test
	public void writeCookieSetSameSiteNull() {
		this.serializer.setSameSite(null);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));

		assertThat(getCookie().getSameSite()).isNull();
	}

	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
		this.serializer.setCookieName(cookieName);
	}

	private Cookie createCookie(String name, String value) {
		if (this.useBase64Encoding && StringUtils.hasLength(value)) {
			value = new String(Base64.getEncoder().encode(value.getBytes()));
		}
		return new Cookie(name, value);
	}

	private MockCookie getCookie() {
		return (MockCookie) this.response.getCookie(this.cookieName);
	}

	private String getCookieValue() {
		String value = getCookie().getValue();
		if (!this.useBase64Encoding) {
			return value;
		}
		if (value == null) {
			return null;
		}
		return new String(Base64.getDecoder().decode(value));
	}

	private CookieValue cookieValue(String cookieValue) {
		return new CookieValue(this.request, this.response, cookieValue);
	}

}
