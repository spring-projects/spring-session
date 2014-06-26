package org.springframework.session.web;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import static org.fest.assertions.Assertions.assertThat;

public class HeaderSessionStrategyTests {
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	private HeaderHttpSessionStrategy strategy;
	private String headerName;
	private Session session;

	@Before
	public void setup() throws Exception {
		headerName = "x-auth-token";
		session = new MapSession();
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		strategy = new HeaderHttpSessionStrategy();
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
	public void getRequestedSessionIdNotNullCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		setSessionId(session.getId());
		assertThat(strategy.getRequestedSessionId(request)).isEqualTo(session.getId());
	}

	@Test
	public void onNewSession() throws Exception {
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	@Test
	public void onNewSessionCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		strategy.onNewSession(session, request, response);
		assertThat(getSessionId()).isEqualTo(session.getId());
	}

	@Test
	public void onDeleteSession() throws Exception {
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test
	public void onDeleteSessionCustomHeaderName() throws Exception {
		setHeaderName("CUSTOM");
		strategy.onInvalidateSession(request, response);
		assertThat(getSessionId()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void setHeaderNameNull() throws Exception {
		strategy.setHeaderName(null);
	}

	public void setHeaderName(String headerName) {
		strategy.setHeaderName(headerName);
		this.headerName = headerName;
	}

	public void setSessionId(String id) {
		request.addHeader(headerName, id);
	}

	public String getSessionId() {
		return response.getHeader(headerName);
	}
}