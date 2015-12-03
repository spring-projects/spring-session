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

import static org.fest.assertions.Assertions.*;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import javax.servlet.http.Cookie;
import java.util.Map;

public class CookieHttpSessionStrategyTests {
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	private CookieHttpSessionStrategy strategy;
	private String cookieName;
	private Session session;

	@Before
	public void setup() throws Exception {
		cookieName = "SESSION";
		session = new MapSession();
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		strategy = new CookieHttpSessionStrategy();
	}

	@Test
	public void getRequestedSessionIdNull() throws Exception {
		assertThat(strategy.getRequestedSessionId(request)).isNull();
	}

	@Test
	public void getRequestedSessionIdNotNull() throws Exception {
		setSessionCookie(session.getId());
		assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
	}

	@Test
	public void getRequestedSessionIdNotNullCustomCookieName() throws Exception {
		setCookieName("CUSTOM");
		setSessionCookie(session.getId());
		assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
	}

	@Test
	public void onNewSession() throws Exception {
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	@Test
	public void onNewSessionTwiceSameId() throws Exception {
		strategy.onNewSession(session, request, response);
		strategy.onNewSession(session, request, response);

		assertThat(response.getCookies()).hasSize(1);
	}

	@Test
	public void onNewSessionTwiceNewId() throws Exception {
		Session newSession = new MapSession();

		strategy.onNewSession(session, request, response);
		strategy.onNewSession(newSession, request, response);

		Cookie[] cookies = response.getCookies();
		assertThat(cookies).hasSize(2);

		assertThat(cookies[0].getValue()).isEqualTo(session.getId());
		assertThat(cookies[1].getValue()).isEqualTo(newSession.getId());
	}

	@Test
	public void onNewSessionExistingSessionSameAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie(existing.getId());
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	@Test
	public void onNewSessionExistingSessionNewAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie(existing.getId());
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo("0 " + existing.getId() + " new " + session.getId());
	}

	// gh-321
	@Test
	public void onNewSessionExplicitAlias() throws Exception {
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo("new " + session.getId());
	}

	@Test
	public void onNewSessionCookiePath() throws Exception {
		request.setContextPath("/somethingunique");
		strategy.onNewSession(session, request, response);

		Cookie sessionCookie = response.getCookie(cookieName);
		assertThat(sessionCookie.getPath()).isEqualTo(request.getContextPath() + "/");
	}

	@Test
	public void onNewSessionCustomCookieName() throws Exception {
		setCookieName("CUSTOM");
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	@Test
	public void onDeleteSession() throws Exception {
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteSessionCookiePath() throws Exception {
		request.setContextPath("/somethingunique");
		strategy.onInvalidateSession(request, response);

		Cookie sessionCookie = response.getCookie(cookieName);
		assertThat(sessionCookie.getPath()).isEqualTo(request.getContextPath() + "/");
	}

	@Test
	public void onDeleteSessionCustomCookieName() throws Exception {
		setCookieName("CUSTOM");
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteSessionExistingSessionSameAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie("0 " + existing.getId() + " new " + session.getId());
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEqualTo(existing.getId());
	}

	@Test
	public void onDeleteSessionExistingSessionNewAlias() throws Exception {
		Session existing = new MapSession();
		setSessionCookie("0 " + existing.getId() + " new " + session.getId());
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEqualTo(existing.getId());
	}

	@SuppressWarnings("deprecation")
	@Test(expected = IllegalArgumentException.class)
	public void setCookieNameNull() throws Exception {
		strategy.setCookieName(null);
	}

	@Test
	public void encodeURLNoExistingQuery() {
		assertThat(strategy.encodeURL("/url", "2")).isEqualTo("/url?_s=2");
	}

	@Test
	public void encodeURLNoExistingQueryEmpty() {
		assertThat(strategy.encodeURL("/url?", "2")).isEqualTo("/url?_s=2");
	}

	@Test
	public void encodeURLExistingQueryNoAlias() {
		assertThat(strategy.encodeURL("/url?a=b", "2")).isEqualTo("/url?a=b&_s=2");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasStart() {
		assertThat(strategy.encodeURL("/url?_s=1&y=z", "2")).isEqualTo("/url?_s=2&y=z");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasMiddle() {
		assertThat(strategy.encodeURL("/url?a=b&_s=1&y=z", "2")).isEqualTo("/url?a=b&_s=2&y=z");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasEnd() {
		assertThat(strategy.encodeURL("/url?a=b&_s=1", "2")).isEqualTo("/url?a=b&_s=2");
	}

	//

	@Test
	public void encodeURLExistingQueryParamEndsWithActualParamStart() {
		assertThat(strategy.encodeURL("/url?x_s=1&y=z", "2")).isEqualTo("/url?x_s=1&y=z&_s=2");
	}

	@Test
	public void encodeURLExistingQueryParamEndsWithActualParamMiddle() {
		assertThat(strategy.encodeURL("/url?a=b&x_s=1&y=z", "2")).isEqualTo("/url?a=b&x_s=1&y=z&_s=2");
	}

	@Test
	public void encodeURLExistingQueryParamEndsWithActualParamEnd() {
		assertThat(strategy.encodeURL("/url?a=b&x_s=1", "2")).isEqualTo("/url?a=b&x_s=1&_s=2");
	}

	//

	@Test
	public void encodeURLNoExistingQueryDefaultAlias() {
		assertThat(strategy.encodeURL("/url", "0")).isEqualTo("/url");
	}

	@Test
	public void encodeURLNoExistingQueryEmptyDefaultAlias() {
		assertThat(strategy.encodeURL("/url?", "0")).isEqualTo("/url?");
	}

	@Test
	public void encodeURLExistingQueryNoAliasDefaultAlias() {
		assertThat(strategy.encodeURL("/url?a=b", "0")).isEqualTo("/url?a=b");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasStartDefaultAlias() {
		// relaxed constraint as result /url?&y=z does not hurt anything (ideally should remove the &)
		assertThat(strategy.encodeURL("/url?_s=1&y=z", "0")).doesNotContain("_s=0&_s=1");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasMiddleDefaultAlias() {
		assertThat(strategy.encodeURL("/url?a=b&_s=1&y=z", "0")).isEqualTo("/url?a=b&y=z");
	}

	@Test
	public void encodeURLExistingQueryExistingAliasEndDefaultAlias() {
		assertThat(strategy.encodeURL("/url?a=b&_s=1", "0")).isEqualTo("/url?a=b");
	}

	@Test
	public void encodeURLMaliciousAlias() {
		assertThat(strategy.encodeURL("/url?a=b&_s=1", "\"> <script>alert('hi')</script>")).isEqualTo("/url?a=b&_s=%22%3E+%3Cscript%3Ealert%28%27hi%27%29%3C%2Fscript%3E");
	}

	// --- getCurrentSessionAlias

	@Test
	public void getCurrentSessionAliasNull() {
		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasNullParamName() {
		strategy.setSessionAliasParamName(null);
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "NOT USED");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	// protect against malicious users
	@Test
	public void getCurrentSessionAliasContainsQuote() {
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here\"this");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	 public void getCurrentSessionAliasContainsSingleQuote() {
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here'this");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasContainsSpace() {
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here this");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasContainsLt() {
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here<this");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasContainsGt() {
		strategy.setSessionAliasParamName(null);
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "here>this");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getCurrentSessionAliasTooLong() {
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "012345678901234567890123456789012345678901234567890");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	// We want some sort of length restrictions, but want to ensure some sort of length Technically no hard limit, but chose 50
	@Test
	public void getCurrentSessionAliasAllows50() {
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "01234567890123456789012345678901234567890123456789");

		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo("01234567890123456789012345678901234567890123456789");
	}

	@Test
	public void getCurrentSession() {
		String expectedAlias = "1";
		request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, expectedAlias);
		assertThat(strategy.getCurrentSessionAlias(request)).isEqualTo(expectedAlias);
	}

	// --- getNewSessionAlias

	@Test
	public void getNewSessionAliasNoSessions() {
		assertThat(strategy.getNewSessionAlias(request)).isEqualTo(CookieHttpSessionStrategy.DEFAULT_ALIAS);
	}

	@Test
	public void getNewSessionAliasSingleSession() {
		setSessionCookie("abc");

		assertThat(strategy.getNewSessionAlias(request)).isEqualTo("1");
	}

	@Test
	public void getNewSessionAlias2Sessions() {
		setCookieWithNSessions(2);

		assertThat(strategy.getNewSessionAlias(request)).isEqualTo("2");
	}

	@Test
	public void getNewSessionAlias9Sessions() {
		setCookieWithNSessions(9);

		assertThat(strategy.getNewSessionAlias(request)).isEqualToIgnoringCase("9");
	}

	@Test
	public void getNewSessionAlias10Sessions() {
		setCookieWithNSessions(10);

		assertThat(strategy.getNewSessionAlias(request)).isEqualToIgnoringCase("a");
	}

	@Test
	public void getNewSessionAlias16Sessions() {
		setCookieWithNSessions(16);

		assertThat(strategy.getNewSessionAlias(request)).isEqualToIgnoringCase("10");
	}

	@Test
	public void getNewSessionAliasInvalidAlias() {
		setSessionCookie("0 1 $ b");

		assertThat(strategy.getNewSessionAlias(request)).isEqualToIgnoringCase("1");
	}

	// --- getSessionIds

	@Test
	public void getSessionIdsNone() {
		assertThat(strategy.getSessionIds(request)).isEmpty();
	}

	@Test
	public void getSessionIdsSingle() {
		String expectedId = "a";
		setSessionCookie(expectedId);

		Map<String, String> sessionIds = strategy.getSessionIds(request);
		assertThat(sessionIds.size()).isEqualTo(1);
		assertThat(sessionIds.get("0")).isEqualTo(expectedId);
	}

	@Test
	public void getSessionIdsMulti() {
		setSessionCookie("0 a 1 b");

		Map<String, String> sessionIds = strategy.getSessionIds(request);
		assertThat(sessionIds.size()).isEqualTo(2);
		assertThat(sessionIds.get("0")).isEqualTo("a");
		assertThat(sessionIds.get("1")).isEqualTo("b");
	}

	@Test
	public void getSessionIdsDangling() {
		setSessionCookie("0 a 1 b noValue");

		Map<String, String> sessionIds = strategy.getSessionIds(request);
		assertThat(sessionIds.size()).isEqualTo(2);
		assertThat(sessionIds.get("0")).isEqualTo("a");
		assertThat(sessionIds.get("1")).isEqualTo("b");
	}

	// --- helper

	@Test
	public void createSessionCookieValue() {
		assertThat(createSessionCookieValue(17)).isEqualToIgnoringCase("0 0 1 1 2 2 3 3 4 4 5 5 6 6 7 7 8 8 9 9 a 10 b 11 c 12 d 13 e 14 f 15 10 16");
	}

	private void setCookieWithNSessions(long size) {
		setSessionCookie(createSessionCookieValue(size));
	}

	private String createSessionCookieValue(long size) {
		StringBuffer buffer = new StringBuffer();

		for(long i=0;i < size; i++) {
			String hex = Long.toHexString(i);
			buffer.append(hex);
			buffer.append(" ");
			buffer.append(i);
			if(i < size - 1) {
				buffer.append(" ");
			}
		}

		return buffer.toString();
	}

	@SuppressWarnings("deprecation")
	public void setCookieName(String cookieName) {
		strategy.setCookieName(cookieName);
		this.cookieName = cookieName;
	}

	public void setSessionCookie(String value) {
		request.setCookies(new Cookie(cookieName, value));
	}

	public String getSessionId() {
		return response.getCookie(cookieName).getValue();
	}
}