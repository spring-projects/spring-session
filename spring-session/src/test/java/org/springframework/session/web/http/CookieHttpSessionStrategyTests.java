package org.springframework.session.web.http;

import static org.fest.assertions.Assertions.*;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.web.http.CookieHttpSessionStrategy;

import javax.servlet.http.Cookie;

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
        setSessionId(session.getId());
        assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
    }

    @Test
    public void getRequestedSessionIdNotNullCustomCookieName() throws Exception {
        setCookieName("CUSTOM");
        setSessionId(session.getId());
        assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
    }

    @Test
    public void onNewSession() throws Exception {
        strategy.onNewSession(session, request, response);
        assertThat(getSessionId()).isEqualTo(session.getId());
    }

    @Test
    public void onNewSessionExistingSessionSameAlias() throws Exception {
        Session existing = new MapSession();
        setSessionId(existing.getId());
        strategy.onNewSession(session, request, response);
        assertThat(getSessionId()).isEqualTo(session.getId());
    }

    @Test
    public void onNewSessionExistingSessionNewAlias() throws Exception {
        Session existing = new MapSession();
        setSessionId(existing.getId());
        request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
        strategy.onNewSession(session, request, response);
        assertThat(getSessionId()).isEqualTo("0 " + existing.getId() + " new " + session.getId());
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
        setSessionId("0 " + existing.getId() + " new " + session.getId());
        strategy.onInvalidateSession(request, response);
        assertThat(getSessionId()).isEqualTo(session.getId());
    }

    @Test
    public void onDeleteSessionExistingSessionNewAlias() throws Exception {
        Session existing = new MapSession();
        setSessionId("0 " + existing.getId() + " new " + session.getId());
        request.setParameter(CookieHttpSessionStrategy.DEFAULT_SESSION_ALIAS_PARAM_NAME, "new");
        strategy.onInvalidateSession(request, response);
        assertThat(getSessionId()).isEqualTo(existing.getId());
    }

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


    // --- helper

    public void setCookieName(String cookieName) {
        strategy.setCookieName(cookieName);
        this.cookieName = cookieName;
    }

    public void setSessionId(String id) {
        request.setCookies(new Cookie(cookieName, id));
    }

    public String getSessionId() {
        return response.getCookie(cookieName).getValue();
    }
}