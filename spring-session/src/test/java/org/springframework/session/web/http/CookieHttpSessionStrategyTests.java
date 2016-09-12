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

package org.springframework.session.web.http;

import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieHttpSessionStrategyTests {
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	private CookieHttpSessionStrategy strategy;
	private String cookieName;
	private Session session;

	@Before
	public void setup() throws Exception {
		this.cookieName = "SESSION";
		this.session = new MapSession();
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.strategy = new CookieHttpSessionStrategy();
	}

	@Test
	public void getRequestedSessionIdNull() throws Exception {
		assertThat(this.strategy.getRequestedSessionId(this.request)).isNull();
	}

	@Test
	public void getRequestedSessionIdNotNull() throws Exception {
		setSessionCookie(this.session.getId());
		assertThat(this.strategy.getRequestedSessionId(this.request))
				.isEqualTo(this.session.getId());
	}

	@Test
	public void getRequestedSessionIdNotNullCustomCookieName() throws Exception {
		setCookieName("CUSTOM");
		setSessionCookie(this.session.getId());
		assertThat(this.strategy.getRequestedSessionId(this.request))
				.isEqualTo(this.session.getId());
	}

	@Test
	public void onNewSession() throws Exception {
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId()).isEqualTo(this.session.getId());
	}

	@Test
	public void onNewSessionTwiceSameId() throws Exception {
		this.strategy.onNewSession(this.session, this.request, this.response);
		this.strategy.onNewSession(this.session, this.request, this.response);

		assertThat(this.response.getCookies()).hasSize(1);
	}

	@Test
	public void onNewSessionTwiceNewId() throws Exception {
		Session newSession = new MapSession();

		this.strategy.onNewSession(this.session, this.request, this.response);
		this.strategy.onNewSession(newSession, this.request, this.response);

		Cookie[] cookies = this.response.getCookies();
		assertThat(cookies).hasSize(2);

		assertThat(cookies[0].getValue()).isEqualTo(this.session.getId());
		assertThat(cookies[1].getValue()).isEqualTo(newSession.getId());
	}

	@Test
	public void onNewSessionExistingSessionSameAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie(existing.getId());
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId()).isEqualTo(this.session.getId());
	}

	@Test
	public void onNewSessionExistingSessionNewAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie(existing.getId());
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId())
				.isEqualTo("0 " + existing.getId() + " new " + this.session.getId());
	}

	@Test
	public void onNewSessionExistingSessionNewAliasCustomDelimiter() throws Exception {
		this.strategy.setSerializationDelimiter("_");
		Session existing = new MapSession();
		setSessionCookie(existing.getId());
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId())
				.isEqualTo("0_" + existing.getId() + "_new_" + this.session.getId());
	}

	// gh-321
	@Test
	public void onNewSessionExplicitAlias() throws Exception {
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId()).isEqualTo("new " + this.session.getId());
	}

	@Test
	public void onNewSessionCookiePath() throws Exception {
		this.request.setContextPath("/somethingunique");
		this.strategy.onNewSession(this.session, this.request, this.response);

		Cookie sessionCookie = this.response.getCookie(this.cookieName);
		assertThat(sessionCookie.getPath())
				.isEqualTo(this.request.getContextPath() + "/");
	}

	@Test
	public void onNewSessionCustomCookieName() throws Exception {
		setCookieName("CUSTOM");
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId()).isEqualTo(this.session.getId());
	}

	@Test
	public void onDeleteSession() throws Exception {
		this.strategy.onInvalidateSession(this.request, this.response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteSessionCookiePath() throws Exception {
		this.request.setContextPath("/somethingunique");
		this.strategy.onInvalidateSession(this.request, this.response);

		Cookie sessionCookie = this.response.getCookie(this.cookieName);
		assertThat(sessionCookie.getPath())
				.isEqualTo(this.request.getContextPath() + "/");
	}

	@Test
	public void onDeleteSessionCustomCookieName() throws Exception {
		setCookieName("CUSTOM");
		this.strategy.onInvalidateSession(this.request, this.response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteSessionExistingSessionSameAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie("0 " + existing.getId() + " new " + this.session.getId());
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		this.strategy.onInvalidateSession(this.request, this.response);
		assertThat(getSessionId()).isEqualTo(existing.getId());
	}

	@Test
	public void onDeleteSessionExistingSessionNewAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie("0 " + existing.getId() + " new " + this.session.getId());
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		this.strategy.onInvalidateSession(this.request, this.response);
		assertThat(getSessionId()).isEqualTo(existing.getId());
	}

	@SuppressWarnings("deprecation")
	@Test(expected = IllegalArgumentException.class)
	public void setCookieNameNull() throws Exception {
		this.strategy.setCookieName(null);
	}

	@Test
	public void encodeURLNoExistingQuery() {
		assertThat(this.strategy.encodeURL("/url", "2")).isEqualTo("/url?_s=2");
	}

	@Test
	public void encodeURLNoExistingQueryEmpty() {
		assertThat(this.strategy.encodeURL("/url?", "2")).isEqualTo("/url?_s=2");
	}

	@Test
	public void encodeURLExistingQueryNoAlias() {
		assertThat(this.strategy.encodeURL("/url?a=b", "2")).isEqualTo("/url?a=b&_s=2");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasStart() {
		assertThat(this.strategy.encodeURL("/url?_s=1&y=z", "2"))
				.isEqualTo("/url?_s=2&y=z");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasMiddle() {
		assertThat(this.strategy.encodeURL("/url?a=b&_s=1&y=z", "2"))
				.isEqualTo("/url?a=b&_s=2&y=z");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasEnd() {
		assertThat(this.strategy.encodeURL("/url?a=b&_s=1", "2"))
				.isEqualTo("/url?a=b&_s=2");
	}

	//

	@Test
	public void encodeURLExistingQueryParamEndsWithActualParamStart() {
		assertThat(this.strategy.encodeURL("/url?x_s=1&y=z", "2"))
				.isEqualTo("/url?x_s=1&y=z&_s=2");
	}

	@Test
	public void encodeURLExistingQueryParamEndsWithActualParamMiddle() {
		assertThat(this.strategy.encodeURL("/url?a=b&x_s=1&y=z", "2"))
				.isEqualTo("/url?a=b&x_s=1&y=z&_s=2");
	}

	@Test
	public void encodeURLExistingQueryParamEndsWithActualParamEnd() {
		assertThat(this.strategy.encodeURL("/url?a=b&x_s=1", "2"))
				.isEqualTo("/url?a=b&x_s=1&_s=2");
	}

	//

	@Test
	public void encodeURLNoExistingQueryDefaultAlias() {
		assertThat(this.strategy.encodeURL("/url", "0")).isEqualTo("/url");
	}

	@Test
	public void encodeURLNoExistingQueryEmptyDefaultAlias() {
		assertThat(this.strategy.encodeURL("/url?", "0")).isEqualTo("/url?");
	}

	@Test
	public void encodeURLExistingQueryNoAliasDefaultAlias() {
		assertThat(this.strategy.encodeURL("/url?a=b", "0")).isEqualTo("/url?a=b");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasStartDefaultAlias() {
		// relaxed constraint as result /url?&y=z does not hurt anything (ideally should
		// remove the &)
		assertThat(this.strategy.encodeURL("/url?_s=1&y=z", "0"))
				.doesNotContain("_s=0&_s=1");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasMiddleDefaultAlias() {
		assertThat(this.strategy.encodeURL("/url?a=b&_s=1&y=z", "0"))
				.isEqualTo("/url?a=b&y=z");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasEndDefaultAlias() {
		assertThat(this.strategy.encodeURL("/url?a=b&_s=1", "0")).isEqualTo("/url?a=b");
	}

	@Test
	public void encodeURLWithSameAlias() {
		String url = String.format("/url?%s=1",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		assertThat(this.strategy.encodeURL(url, "1")).isEqualTo(url);
	}

	@Test
	public void encodeURLWithSameAliasOtherQueryParamsBefore() {
		String url = String.format("/url?a=b&%s=1",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		assertThat(this.strategy.encodeURL(url, "1")).isEqualTo(url);
	}

	@Test
	public void encodeURLWithSameAliasOtherQueryParamsAfter() {
		String url = String.format("/url?%s=1&a=b",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		assertThat(this.strategy.encodeURL(url, "1")).isEqualTo(url);
	}

	@Test
	public void encodeURLWithSameAliasOtherQueryParamsBeforeAndAfter() {
		String url = String.format("/url?a=b&%s=1&c=d",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		assertThat(this.strategy.encodeURL(url, "1")).isEqualTo(url);
	}

	@Test
	public void encodeURLMaliciousAlias() {
		assertThat(this.strategy.encodeURL("/url?a=b&_s=1",
				"\"> <script>alert('hi')</script>")).isEqualTo(
						"/url?a=b&_s=%22%3E+%3Cscript%3Ealert%28%27hi%27%29%3C%2Fscript%3E");
	}

	// --- getCurrentSessionAlias

	@Test
	public void getCurrentSessionAliasNull() {
		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasNullParamName() {
		this.strategy.setSessionAliasParamName(null);
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "NOT USED");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	// protect against malicious users
	@Test
	public void getCurrentSessionAliasContainsQuote() {
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here\"this");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasContainsSingleQuote() {
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here'this");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasContainsSpace() {
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here this");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasContainsLt() {
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here<this");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasContainsGt() {
		this.strategy.setSessionAliasParamName(null);
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here>this");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasTooLong() {
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME,
				"012345678901234567890123456789012345678901234567890");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	// We want some sort of length restrictions, but want to ensure some sort of length
	// Technically no hard limit, but chose 50
	@Test
	public void getCurrentSessionAliasAllows50() {
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME,
				"01234567890123456789012345678901234567890123456789");

		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo("01234567890123456789012345678901234567890123456789");
	}

	@Test
	public void getCurrentSession() {
		String expectedAlias = "1";
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME,
				expectedAlias);
		assertThat(this.strategy.getCurrentSessionAlias(this.request))
				.isEqualTo(expectedAlias);
	}

	// --- getNewSessionAlias

	@Test
	public void getNewSessionAliasNoSessions() {
		assertThat(this.strategy.getNewSessionAlias(this.request))
				.isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getNewSessionAliasSingleSession() {
		setSessionCookie("abc");

		assertThat(this.strategy.getNewSessionAlias(this.request)).isEqualTo("1");
	}

	@Test
	public void getNewSessionAlias2Sessions() {
		setCookieWithNSessions(2);

		assertThat(this.strategy.getNewSessionAlias(this.request)).isEqualTo("2");
	}

	@Test
	public void getNewSessionAlias9Sessions() {
		setCookieWithNSessions(9);

		assertThat(this.strategy.getNewSessionAlias(this.request))
				.isEqualToIgnoringCase("9");
	}

	@Test
	public void getNewSessionAlias10Sessions() {
		setCookieWithNSessions(10);

		assertThat(this.strategy.getNewSessionAlias(this.request))
				.isEqualToIgnoringCase("a");
	}

	@Test
	public void getNewSessionAlias16Sessions() {
		setCookieWithNSessions(16);

		assertThat(this.strategy.getNewSessionAlias(this.request))
				.isEqualToIgnoringCase("10");
	}

	@Test
	public void getNewSessionAliasInvalidAlias() {
		setSessionCookie("0 1 $ b");

		assertThat(this.strategy.getNewSessionAlias(this.request))
				.isEqualToIgnoringCase("1");
	}

	// --- getSessionIds

	@Test
	public void getSessionIdsNone() {
		assertThat(this.strategy.getSessionIds(this.request)).isEmpty();
	}

	@Test
	public void getSessionIdsSingle() {
		String expectedId = "a";
		setSessionCookie(expectedId);

		Map<String, String> sessionIds = this.strategy.getSessionIds(this.request);
		assertThat(sessionIds.size()).isEqualTo(1);
		assertThat(sessionIds.get("0")).isEqualTo(expectedId);
	}

	@Test
	public void getSessionIdsMulti() {
		setSessionCookie("0 a 1 b");

		Map<String, String> sessionIds = this.strategy.getSessionIds(this.request);
		assertThat(sessionIds.size()).isEqualTo(2);
		assertThat(sessionIds.get("0")).isEqualTo("a");
		assertThat(sessionIds.get("1")).isEqualTo("b");
	}

	@Test
	public void getSessionIdsMultiCustomDelimeter() {
		this.strategy.setDeserializationDelimiter("_");
		setSessionCookie("0_a_1_b");

		Map<String, String> sessionIds = this.strategy.getSessionIds(this.request);
		assertThat(sessionIds.size()).isEqualTo(2);
		assertThat(sessionIds.get("0")).isEqualTo("a");
		assertThat(sessionIds.get("1")).isEqualTo("b");
	}

	@Test
	public void getSessionIdsMultiCustomDelimeterMigration() {
		this.strategy.setDeserializationDelimiter("_ ");
		this.strategy.setSerializationDelimiter("_");

		// can parse the old way
		setSessionCookie("0 a 1 b");

		Map<String, String> sessionIds = this.strategy.getSessionIds(this.request);
		assertThat(sessionIds.size()).isEqualTo(2);
		assertThat(sessionIds.get("0")).isEqualTo("a");
		assertThat(sessionIds.get("1")).isEqualTo("b");

		// can parse the new way
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		setSessionCookie("0_a_1_b");

		sessionIds = this.strategy.getSessionIds(this.request);
		assertThat(sessionIds.size()).isEqualTo(2);
		assertThat(sessionIds.get("0")).isEqualTo("a");
		assertThat(sessionIds.get("1")).isEqualTo("b");

		// writes the new way
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		Session existing = new MapSession();
		setSessionCookie(existing.getId());
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		this.strategy.onNewSession(this.session, this.request, this.response);
		assertThat(getSessionId())
				.isEqualTo("0_" + existing.getId() + "_new_" + this.session.getId());

	}

	@Test
	public void getSessionIdsDangling() {
		setSessionCookie("0 a 1 b noValue");

		Map<String, String> sessionIds = this.strategy.getSessionIds(this.request);
		assertThat(sessionIds.size()).isEqualTo(2);
		assertThat(sessionIds.get("0")).isEqualTo("a");
		assertThat(sessionIds.get("1")).isEqualTo("b");
	}

	// --- helper

	@Test
	public void createSessionCookieValue() {
		assertThat(createSessionCookieValue(17)).isEqualToIgnoringCase(
				"0 0 1 1 2 2 3 3 4 4 5 5 6 6 7 7 8 8 9 9 a 10 b 11 c 12 d 13 e 14 f 15 10 16");
	}

	@Test
	public void responseEncodeRedirectUrlWhereRedirectUrlDoesntContainAliasCurrentReqNoAlias() {
		String url = "http://www.somehost.com/some/path";
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedRedirectUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedRedirectUrl).isEqualTo(url);
	}

	@Test
	public void responseEncodeRedirectUrlWhereRedirectUrlDoesntContainAliasCurrentReqHasAlias() {
		String url = "http://www.somehost.com/some/path";
		String alias = "1";
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias);
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedRedirectUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedRedirectUrl).isEqualTo(String.format("%s?%s=%s", url,
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias));
	}

	@Test
	public void responseEncodeRedirectUrlWhereRedirectUrlContainsAliasCurrentReqHasNoAlias() {
		String url = String.format("http://www.somehost.com/some/path?%s=5",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "4");
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedRedirectUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedRedirectUrl).isEqualTo(url);
	}

	@Test
	public void responseEncodeRedirectUrlWhereRedirectUrlDoesntContainAliasCurrentReqNoAliasWithOtherParams() {
		String url = "http://www.somehost.com/some/path?a=b";
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedRedirectUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedRedirectUrl).isEqualTo(url);
	}

	@Test
	public void responseEncodeRedirectUrlWhereRedirectUrlDoesntContainAliasCurrentReqHasAliasWithOtherParams() {
		String url = "http://www.somehost.com/some/path?a=b";
		String alias = "1";
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias);
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedRedirectUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedRedirectUrl).isEqualTo(String.format("%s&%s=%s", url,
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias));
	}

	@Test
	public void responseEncodeRedirectUrlWhereRedirectUrlContainsAliasCurrentReqHasNoAliasWithOtherParams() {
		String url = String.format("http://www.somehost.com/some/path?a=b&%s=5&c=d",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "4");
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedRedirectUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedRedirectUrl).isEqualTo(url);
	}

	@Test
	public void responseEncodeUrlWhereRedirectUrlDoesntContainAliasCurrentReqNoAlias() {
		String url = "http://www.somehost.com/some/path";
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedUrl).isEqualTo(url);
	}

	@Test
	public void responseEncodeUrlWhereRedirectUrlDoesntContainAliasCurrentReqHasAlias() {
		String url = "http://www.somehost.com/some/path";
		String alias = "1";
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias);
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedUrl).isEqualTo(String.format("%s?%s=%s", url,
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias));
	}

	@Test
	public void responseEncodeUrlWhereRedirectUrlContainsAliasCurrentReqHasNoAlias() {
		String url = String.format("http://www.somehost.com/some/path?%s=5",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "4");
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedUrl).isEqualTo(url);
	}

	@Test
	public void responseEncodeUrlWhereRedirectUrlDoesntContainAliasCurrentReqNoAliasWithOtherParams() {
		String url = "http://www.somehost.com/some/path?a=b";
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedUrl).isEqualTo(url);
	}

	@Test
	public void responseEncodeUrlWhereRedirectUrlDoesntContainAliasCurrentReqHasAliasWithOtherParams() {
		String url = "http://www.somehost.com/some/path?a=b";
		String alias = "1";
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias);
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedUrl).isEqualTo(String.format("%s&%s=%s", url,
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, alias));
	}

	@Test
	public void responseEncodeUrlWhereRedirectUrlContainsAliasCurrentReqHasNoAliasWithOtherParams() {
		String url = String.format("http://www.somehost.com/some/path?a=b&%s=5&c=d",
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME);
		this.request.setParameter(
				CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "4");
		HttpServletResponse wrappedResponse = this.strategy.wrapResponse(this.request,
				this.response);
		String encodedUrl = wrappedResponse.encodeRedirectURL(url);
		assertThat(encodedUrl).isEqualTo(url);
	}

	private void setCookieWithNSessions(long size) {
		setSessionCookie(createSessionCookieValue(size));
	}

	private String createSessionCookieValue(long size) {
		StringBuffer buffer = new StringBuffer();

		for (long i = 0; i < size; i++) {
			String hex = Long.toHexString(i);
			buffer.append(hex);
			buffer.append(" ");
			buffer.append(i);
			if (i < size - 1) {
				buffer.append(" ");
			}
		}

		return buffer.toString();
	}

	@SuppressWarnings("deprecation")
	public void setCookieName(String cookieName) {
		this.strategy.setCookieName(cookieName);
		this.cookieName = cookieName;
	}

	public void setSessionCookie(String value) {
		this.request.setCookies(new Cookie(this.cookieName, value));
	}

	public String getSessionId() {
		return this.response.getCookie(this.cookieName).getValue();
	}
}
