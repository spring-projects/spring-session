package org.springframework.session.web.http;

import static org.fest.assertions.Assertions.assertThat;

import javax.servlet.http.Cookie;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

public class CookieMultipleHttpSessionStrategyTests {
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	private CookieMultipleHttpSessionStrategy strategy;
	private String sessionsCookieName;
	private String currentCookieName;
	private Session session;
	private Session expiredSession;

	private final static String DELIM = "~";

	@Before
	public void setup() throws Exception {
		sessionsCookieName = "SESSIONS";
		currentCookieName = "CURRENT_SESSION";
		session = new MapSession();
		expiredSession = new MapSession();
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		strategy = new CookieMultipleHttpSessionStrategy();
	}

	@Test
	public void getRequestedSessionIdNull() throws Exception {
		assertThat(strategy.getRequestedSessionId(request)).isNull();
	}

	@Test
	public void getRequestedSessionIdNotNull() throws Exception {
		setSessionId(session.getId());
		assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
	}

	@Test
	public void getRequestedSessionIdNotNullCustomCookieName() throws Exception {
		setCookieNames("CUSTOM_SESSIONS", "CUSTOM_CURRENT_SESSION");
		setSessionId(session.getId());
		assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
	}

	@Test
	public void onNewSession() throws Exception {
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
		assertThat(getSessionIds()).isEqualTo(session.getId());
	}

	@Test
	public void onMultipleNewSessions() throws Exception {
		strategy.onNewSession(expiredSession, request, response);
		String first = expiredSession.getId();
		assertThat(getSessionId()).isEqualTo(first);
		assertThat(getSessionIds()).isEqualTo(first);

		setSessionIds(first, first);
		String second = session.getId();
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(second);
		assertThat(getSessionIds()).isEqualTo(first + DELIM + second);
	}

	@Test
	public void onNewSessionCookiePath() throws Exception {
		request.setContextPath("/somethingunique");
		strategy.onNewSession(session, request, response);

		Cookie currentSessionCookie = response.getCookie(currentCookieName);
		assertThat(currentSessionCookie.getPath()).isEqualTo(request.getContextPath() + "/");
	}

	@Test
	public void onNewSessionCustomCookieName() throws Exception {
		setCookieNames("CUSTOM_SESSIONS", "CUSTOM_CURRENT_SESSION");
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	@Test
	public void onDeleteSession() throws Exception {
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteOneOfMultipleSessions() throws Exception {
		setSessionIds(expiredSession.getId() + DELIM + session.getId(), expiredSession.getId());
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
		assertThat(getSessionIds()).isEqualTo(session.getId());
	}
	
	@Test
	public void onDeleteAllSessions() throws Exception {
		setSessionIds(expiredSession.getId() + DELIM + session.getId(), expiredSession.getId());
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
		assertThat(getSessionIds()).isEqualTo(session.getId());
		
		setSessionIds(getSessionIds(), session.getId());
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
		assertThat(getSessionIds()).isEmpty();
	}

	@Test
	public void onDeleteSessionCookiePath() throws Exception {
		request.setContextPath("/somethingunique");
		strategy.onInvalidateSession(request, response);

		Cookie currentSessionCookie = response.getCookie(currentCookieName);
		assertThat(currentSessionCookie.getPath()).isEqualTo(request.getContextPath() + "/");
	}

	@Test
	public void onDeleteSessionCustomCookieName() throws Exception {
		setCookieNames("CUSTOM_SESSIONS", "CUSTOM_CURRENT_SESSION");
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void setCookieNameNull() throws Exception {
		strategy.setSessionsCookieName(null);
		strategy.setCurrentCookieName(null);
	}

	public void setCookieNames(String sessionsCookieName, String currentCookieName) {
		strategy.setSessionsCookieName(sessionsCookieName);
		strategy.setCurrentCookieName(currentCookieName);
		this.sessionsCookieName = sessionsCookieName;
		this.currentCookieName = currentCookieName;
	}

	public void setSessionId(String id) {
		request.setCookies(new Cookie(sessionsCookieName, expiredSession.getId() + DELIM + id), new Cookie(
				currentCookieName, id));
	}

	public void setSessionIds(String ids, String id) {
		request.setCookies(new Cookie(sessionsCookieName, ids), new Cookie(currentCookieName, id));
	}

	public String getSessionId() {
		return getLatestCookie(currentCookieName);
	}

	public String getSessionIds() {
		return getLatestCookie(sessionsCookieName);
	}

	private String getLatestCookie(String name) {
		Cookie[] cookies = response.getCookies();
		Cookie foundCookie = null;
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals(name)) {
					foundCookie = cookie;
				}
			}
		}
		return foundCookie != null ? foundCookie.getValue() : null;
	}
}