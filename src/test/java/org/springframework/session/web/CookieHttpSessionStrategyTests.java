package org.springframework.session.web;

import static org.fest.assertions.Assertions.*;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

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
    public void onDeleteSessionCustomCookieName() throws Exception {
        setCookieName("CUSTOM");
        strategy.onInvalidateSession(request, response);
        assertThat(getSessionId()).isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void setCookieNameNull() throws Exception {
        strategy.setCookieName(null);
    }

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