/*
 * Copyright 2014-2021 the original author or authors.
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link DefaultCookieSerializer}.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @author Eddú Meléndez
 */
class DefaultCookieSerializerTests {

	private String cookieName;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private DefaultCookieSerializer serializer;

	private String sessionId;

	@BeforeEach
	void setup() {
		this.cookieName = "SESSION";
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.sessionId = "sessionId";
		this.serializer = new DefaultCookieSerializer();
	}

	// --- readCookieValues ---

	@Test
	void readCookieValuesNull() {
		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieValuesSingle(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		this.request.setCookies(createCookie(this.cookieName, this.sessionId, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsOnly(this.sessionId);
	}

	@Test
	void readCookieSerializerUseBase64EncodingTrueValuesNotBase64() {
		this.sessionId = "&^%$*";
		this.serializer.setUseBase64Encoding(true);
		this.request.setCookies(new Cookie(this.cookieName, this.sessionId));
		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieValuesSingleAndInvalidName(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		this.request.setCookies(createCookie(this.cookieName, this.sessionId, useBase64Encoding),
				createCookie(this.cookieName + "INVALID", this.sessionId + "INVALID", useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsOnly(this.sessionId);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieValuesMulti(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		String secondSession = "secondSessionId";
		this.request.setCookies(createCookie(this.cookieName, this.sessionId, useBase64Encoding),
				createCookie(this.cookieName, secondSession, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsExactly(this.sessionId, secondSession);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieValuesMultiCustomSessionCookieName(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		setCookieName("JSESSIONID");
		String secondSession = "secondSessionId";
		this.request.setCookies(createCookie(this.cookieName, this.sessionId, useBase64Encoding),
				createCookie(this.cookieName, secondSession, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsExactly(this.sessionId, secondSession);
	}

	// gh-392
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieValuesNullCookieValue(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		this.request.setCookies(createCookie(this.cookieName, null, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieValuesNullCookieValueAndJvmRoute(boolean useBase64Encoding) {
		this.serializer.setJvmRoute("123");
		this.request.setCookies(createCookie(this.cookieName, null, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieValuesNullCookieValueAndNotNullCookie(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		this.serializer.setJvmRoute("123");
		this.request.setCookies(createCookie(this.cookieName, null, useBase64Encoding),
				createCookie(this.cookieName, this.sessionId, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsOnly(this.sessionId);
	}

	// --- writeCookie ---

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void writeCookie(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookieValue(useBase64Encoding)).isEqualTo(this.sessionId);
	}

	// --- httpOnly ---

	@Test
	void writeCookieHttpOnlyDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().isHttpOnly()).isTrue();
	}

	@Test
	void writeCookieHttpOnlySetTrue() {
		this.serializer.setUseHttpOnlyCookie(true);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().isHttpOnly()).isTrue();
	}

	@Test
	void writeCookieHttpOnlySetFalse() {
		this.serializer.setUseHttpOnlyCookie(false);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().isHttpOnly()).isFalse();
	}

	// --- domainName ---

	@Test
	void writeCookieDomainNameDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getDomain()).isNull();
	}

	@Test
	void writeCookieDomainNameCustom() {
		String domainName = "example.com";
		this.serializer.setDomainName(domainName);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getDomain()).isEqualTo(domainName);
	}

	@Test
	void setDomainNameAndDomainNamePatternThrows() {
		this.serializer.setDomainName("example.com");
		assertThatIllegalStateException().isThrownBy(() -> this.serializer.setDomainNamePattern(".*"))
				.withMessage("Cannot set both domainName and domainNamePattern");
	}

	// --- domainNamePattern ---

	@Test
	void writeCookieDomainNamePattern() {
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
	void setDomainNamePatternAndDomainNameThrows() {
		this.serializer.setDomainNamePattern(".*");
		assertThatIllegalStateException().isThrownBy(() -> this.serializer.setDomainName("example.com"))
				.withMessage("Cannot set both domainName and domainNamePattern");
	}

	// --- cookieName ---

	@Test
	void writeCookieCookieNameDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getName()).isEqualTo("SESSION");
	}

	@Test
	void writeCookieCookieNameCustom() {
		String cookieName = "JSESSIONID";
		setCookieName(cookieName);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getName()).isEqualTo(cookieName);
	}

	@Test
	void setCookieNameNullThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.serializer.setCookieName(null))
				.withMessage("cookieName cannot be null");
	}

	// --- cookiePath ---

	@Test
	void writeCookieCookiePathDefaultEmptyContextPathUsed() {
		this.request.setContextPath("");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getPath()).isEqualTo("/");
	}

	@Test
	void writeCookieCookiePathDefaultContextPathUsed() {
		this.request.setContextPath("/context");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getPath()).isEqualTo("/context");
	}

	@Test
	void writeCookieCookiePathExplicitNullCookiePathContextPathUsed() {
		this.request.setContextPath("/context");
		this.serializer.setCookiePath(null);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getPath()).isEqualTo("/context");
	}

	@Test
	void writeCookieCookiePathExplicitCookiePath() {
		this.request.setContextPath("/context");
		this.serializer.setCookiePath("/");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getPath()).isEqualTo("/");
	}

	// --- cookieMaxAge ---

	@Test
	void writeCookieCookieMaxAgeDefault() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getMaxAge()).isEqualTo(-1);
		assertThat(getCookie().getExpires()).isNull();
	}

	@Test
	void writeCookieCookieMaxAgeExplicit() {
		this.serializer.setClock(Clock.fixed(Instant.parse("2019-10-07T20:10:00Z"), ZoneOffset.UTC));
		this.serializer.setCookieMaxAge(100);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		MockCookie cookie = getCookie();
		assertThat(cookie.getMaxAge()).isEqualTo(100);
		ZonedDateTime expires = cookie.getExpires();
		assertThat(expires).isNotNull();
		assertThat(expires.format(DateTimeFormatter.RFC_1123_DATE_TIME)).isEqualTo("Mon, 7 Oct 2019 20:11:40 GMT");
	}

	@Test
	void writeCookieCookieMaxAgeExplicitEmptyCookie() {
		this.serializer.setClock(Clock.fixed(Instant.parse("2019-10-07T20:10:00Z"), ZoneOffset.UTC));
		this.serializer.setCookieMaxAge(100);
		this.serializer.writeCookieValue(cookieValue(""));
		MockCookie cookie = getCookie();
		assertThat(cookie.getMaxAge()).isEqualTo(0);
		ZonedDateTime expires = cookie.getExpires();
		assertThat(expires).isNotNull();
		assertThat(expires.format(DateTimeFormatter.RFC_1123_DATE_TIME)).isEqualTo("Thu, 1 Jan 1970 00:00:00 GMT");
	}

	@Test
	void writeCookieCookieMaxAgeExplicitCookieValue() {
		this.serializer.setClock(Clock.fixed(Instant.parse("2019-10-07T20:10:00Z"), ZoneOffset.UTC));
		CookieValue cookieValue = cookieValue(this.sessionId);
		cookieValue.setCookieMaxAge(100);
		this.serializer.writeCookieValue(cookieValue);
		MockCookie cookie = getCookie();
		assertThat(cookie.getMaxAge()).isEqualTo(100);
		ZonedDateTime expires = cookie.getExpires();
		assertThat(expires).isNotNull();
		assertThat(expires.format(DateTimeFormatter.RFC_1123_DATE_TIME)).isEqualTo("Mon, 7 Oct 2019 20:11:40 GMT");
	}

	// --- secure ---

	@Test
	void writeCookieDefaultInsecureRequest() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSecure()).isFalse();
	}

	@Test
	void writeCookieSecureSecureRequest() {
		this.request.setSecure(true);
		this.serializer.setUseSecureCookie(true);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSecure()).isTrue();
	}

	@Test
	void writeCookieSecureInsecureRequest() {
		this.serializer.setUseSecureCookie(true);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSecure()).isTrue();
	}

	@Test
	void writeCookieInsecureSecureRequest() {
		this.request.setSecure(true);
		this.serializer.setUseSecureCookie(false);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSecure()).isFalse();
	}

	@Test
	void writeCookieInecureInsecureRequest() {
		this.serializer.setUseSecureCookie(false);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSecure()).isFalse();
	}

	// --- jvmRoute ---

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void writeCookieJvmRoute(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookieValue(useBase64Encoding)).isEqualTo(this.sessionId + "." + jvmRoute);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieJvmRoute(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);
		this.request.setCookies(createCookie(this.cookieName, this.sessionId + "." + jvmRoute, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsOnly(this.sessionId);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieJvmRouteRouteMissing(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);
		this.request.setCookies(createCookie(this.cookieName, this.sessionId, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsOnly(this.sessionId);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void readCookieJvmRouteOnlyRoute(boolean useBase64Encoding) {
		this.serializer.setUseBase64Encoding(useBase64Encoding);
		String jvmRoute = "route";
		this.serializer.setJvmRoute(jvmRoute);
		this.request.setCookies(createCookie(this.cookieName, "." + jvmRoute, useBase64Encoding));
		assertThat(this.serializer.readCookieValues(this.request)).containsOnly("");
	}

	// --- rememberMe ---

	@Test
	void writeCookieRememberMeCookieMaxAgeDefault() {
		this.request.setAttribute("rememberMe", true);
		this.serializer.setRememberMeRequestAttribute("rememberMe");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getMaxAge()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	void writeCookieRememberMeCookieMaxAgeOverride() {
		this.request.setAttribute("rememberMe", true);
		this.serializer.setRememberMeRequestAttribute("rememberMe");
		CookieValue cookieValue = cookieValue(this.sessionId);
		cookieValue.setCookieMaxAge(100);
		this.serializer.writeCookieValue(cookieValue);
		assertThat(getCookie().getMaxAge()).isEqualTo(100);
	}

	// --- sameSite ---

	@Test
	void writeCookieDefaultSameSiteLax() {
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSameSite()).isEqualTo("Lax");
	}

	@Test
	void writeCookieSetSameSiteLax() {
		this.serializer.setSameSite("Lax");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSameSite()).isEqualTo("Lax");
	}

	@Test
	void writeCookieSetSameSiteStrict() {
		this.serializer.setSameSite("Strict");
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSameSite()).isEqualTo("Strict");
	}

	@Test
	void writeCookieSetSameSiteNull() {
		this.serializer.setSameSite(null);
		this.serializer.writeCookieValue(cookieValue(this.sessionId));
		assertThat(getCookie().getSameSite()).isNull();
	}

	void setCookieName(String cookieName) {
		this.cookieName = cookieName;
		this.serializer.setCookieName(cookieName);
	}

	private Cookie createCookie(String name, String value, boolean useBase64Encoding) {
		if (useBase64Encoding && StringUtils.hasLength(value)) {
			value = new String(Base64.getEncoder().encode(value.getBytes()));
		}
		return new Cookie(name, value);
	}

	private MockCookie getCookie() {
		return (MockCookie) this.response.getCookie(this.cookieName);
	}

	private String getCookieValue(boolean useBase64Encoding) {
		String value = getCookie().getValue();
		if (!useBase64Encoding) {
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
