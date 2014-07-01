package org.springframework.session.web;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.NoSuchElementException;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

public class SessionRepositoryFilterTests {
	private final static String SESSION_ATTR_NAME = HttpSession.class.getName();

	private SessionRepository sessionRepository;

	private SessionRepositoryFilter filter;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private MockFilterChain chain;

	@Before
	public void setup() throws Exception {
		sessionRepository = new MapSessionRepository();
		filter = new SessionRepositoryFilter(sessionRepository);
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		chain = new MockFilterChain();
	}

	@Test
	public void doFilterCreateDate() throws Exception {
		final String CREATE_ATTR = "create";
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				long creationTime = wrappedRequest.getSession().getCreationTime();
				long now = System.currentTimeMillis();
				assertThat(now - creationTime).isGreaterThanOrEqualTo(0).isLessThan(5000);
				request.setAttribute(CREATE_ATTR, creationTime);
			}
		});

		final long expectedCreationTime = (Long) request.getAttribute(CREATE_ATTR);
		Thread.sleep(50L);
		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				long creationTime = wrappedRequest.getSession().getCreationTime();

				assertThat(creationTime).isEqualTo(expectedCreationTime);
			}
		});
	}

	@Test
	public void doFilterLastAccessedTime() throws Exception {
		final String ACCESS_ATTR = "create";
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				long lastAccessed = wrappedRequest.getSession().getLastAccessedTime();
				assertThat(lastAccessed).isEqualTo(wrappedRequest.getSession().getCreationTime());
				request.setAttribute(ACCESS_ATTR, lastAccessed);
			}
		});

		final long creationTime = (Long) request.getAttribute(ACCESS_ATTR);
		Thread.sleep(10L);
		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				long lastAccessed = wrappedRequest.getSession().getLastAccessedTime();

				assertThat(lastAccessed).isGreaterThan(wrappedRequest.getSession().getCreationTime());
			}
		});
	}

	@Test
	public void doFilterId() throws Exception {
		final String ID_ATTR = "create";
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				String id = wrappedRequest.getSession().getId();
				assertThat(id).isNotNull();
				assertThat(wrappedRequest.getSession().getId()).isEqualTo(id);
				request.setAttribute(ID_ATTR, id);
			}
		});

		final String id = (String) request.getAttribute(ID_ATTR);
		assertThat(getSessionCookie().getValue()).isEqualTo(id);
		setSessionCookie(id);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getId()).isEqualTo(id);
			}
		});
	}

	@Test
	public void doFilterIdChanges() throws Exception {
		final String ID_ATTR = "create";
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				String id = wrappedRequest.getSession().getId();
				request.setAttribute(ID_ATTR, id);
			}
		});

		final String id = (String) request.getAttribute(ID_ATTR);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getId()).isNotEqualTo(id);
			}
		});
	}

	@Test
	public void doFilterServletContext() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				ServletContext context = wrappedRequest.getSession().getServletContext();
				assertThat(context).isSameAs(wrappedRequest.getServletContext());
			}
		});
	}

	@Test
	public void doFilterMaxInactiveIntervalDefault() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				int interval = wrappedRequest.getSession().getMaxInactiveInterval();
				assertThat(interval).isEqualTo(1800); // 30 minute default (same as Tomcat)
			}
		});
	}

	@Test
	public void doFilterMaxInactiveIntervalOverride() throws Exception {
		final int interval = 600;
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession().setMaxInactiveInterval(interval);
				assertThat(wrappedRequest.getSession().getMaxInactiveInterval()).isEqualTo(interval);
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getMaxInactiveInterval()).isEqualTo(interval);
			}
		});
	}

	@Test
	public void doFilterAttribute() throws Exception {
		final String ATTR = "ATTR";
		final String VALUE = "VALUE";
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession().setAttribute(ATTR, VALUE);
				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isEqualTo(VALUE);
				assertThat(Collections.list(wrappedRequest.getSession().getAttributeNames())).containsOnly(ATTR);
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isEqualTo(VALUE);
				assertThat(Collections.list(wrappedRequest.getSession().getAttributeNames())).containsOnly(ATTR);
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isEqualTo(VALUE);

				wrappedRequest.getSession().removeAttribute(ATTR);

				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isNull();
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isNull();
			}
		});
	}

	@Test
	public void doFilterValue() throws Exception {
		final String ATTR = "ATTR";
		final String VALUE = "VALUE";
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession().putValue(ATTR, VALUE);
				assertThat(wrappedRequest.getSession().getValue(ATTR)).isEqualTo(VALUE);
				assertThat(Arrays.asList(wrappedRequest.getSession().getValueNames())).containsOnly(ATTR);
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getValue(ATTR)).isEqualTo(VALUE);
				assertThat(Arrays.asList(wrappedRequest.getSession().getValueNames())).containsOnly(ATTR);
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getValue(ATTR)).isEqualTo(VALUE);

				wrappedRequest.getSession().removeValue(ATTR);

				assertThat(wrappedRequest.getSession().getValue(ATTR)).isNull();
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getValue(ATTR)).isNull();
			}
		});
	}

	@Test
	public void doFilterIsNewTrue() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().isNew()).isTrue();
				assertThat(wrappedRequest.getSession().isNew()).isTrue();
			}
		});
	}

	@Test
	public void doFilterIsNewFalse() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().isNew()).isFalse();
			}
		});
	}

	@Test
	public void doFilterGetSessionNew() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		assertNewSession();
	}

	@Test
	public void doFilterGetSessionTrueNew() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession(true);
			}
		});

		assertNewSession();
	}

	@Test
	public void doFilterGetSessionFalseNew() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession(false);
			}
		});

		assertNoSession();
	}

	@Test
	public void doFilterGetSessionGetSessionFalse() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession(false)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterCookieSecuritySettings() throws Exception {
		request.setSecure(true);
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		Cookie session = getSessionCookie();
		assertThat(session.isHttpOnly()).describedAs("Session Cookie should be HttpOnly").isTrue();
		assertThat(session.getSecure()).describedAs("Session Cookie should be marked as Secure").isTrue();
	}

	@Test
	public void doFilterSessionContext() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSessionContext sessionContext = wrappedRequest.getSession().getSessionContext();
				assertThat(sessionContext).isNotNull();
				assertThat(sessionContext.getSession("a")).isNull();
				assertThat(sessionContext.getIds()).isNotNull();
				assertThat(sessionContext.getIds().hasMoreElements()).isFalse();

				try {
					sessionContext.getIds().nextElement();
					fail("Expected Exception");
				} catch(NoSuchElementException success) {}
			}
		});
	}



	// --- saving

	@Test
	public void doFilterGetAttr() throws Exception {
		final String ATTR_NAME = "attr";
		final String ATTR_VALUE = "value";
		final String ATTR_NAME2 = "attr2";
		final String ATTR_VALUE2 = "value2";

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession().setAttribute(ATTR_NAME, ATTR_VALUE);
				wrappedRequest.getSession().setAttribute(ATTR_NAME2, ATTR_VALUE2);
			}
		});

		assertNewSession();

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR_NAME)).isEqualTo(ATTR_VALUE);
				assertThat(wrappedRequest.getSession().getAttribute(ATTR_NAME2)).isEqualTo(ATTR_VALUE2);
			}
		});
	}

	// --- invalidate

	@Test
	public void doFilterInvalidateInvalidateIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.invalidate();
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateCreationTimeIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.getCreationTime();
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateAttributeIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.getAttribute("attr");
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateValueIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.getValue("attr");
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateAttributeNamesIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.getAttributeNames();
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateValueNamesIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.getValueNames();
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateSetAttributeIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.setAttribute("a", "b");
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidatePutValueIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.putValue("a", "b");
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateRemoveAttributeIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.removeAttribute("name");
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateRemoveValueIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.removeValue("name");
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateNewIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.isNew();
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateLastAccessedTimeIllegalState() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				try {
					session.getLastAccessedTime();
					fail("Expected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	@Test
	public void doFilterInvalidateId() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();
				// no exception
				session.getId();
			}
		});
	}

	@Test
	public void doFilterInvalidateServletContext() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();

				// no exception
				session.getServletContext();
			}
		});
	}

	@Test
	public void doFilterInvalidateSessionContext() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();

				// no exception
				session.getSessionContext();
			}
		});
	}

	@Test
	public void doFilterInvalidateMaxInteractiveInterval() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.invalidate();

				// no exception
				session.getMaxInactiveInterval();
				session.setMaxInactiveInterval(3600);
			}
		});
	}

	@Test
	public void doFilterInvalidateAndGetSession() throws Exception {
		final String ATTR_NAME = "attr";
		final String ATTR_VALUE = "value";
		final String ATTR_NAME2 = "attr2";
		final String ATTR_VALUE2 = "value2";

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession().setAttribute(ATTR_NAME, ATTR_VALUE);
				wrappedRequest.getSession().invalidate();
				wrappedRequest.getSession().setAttribute(ATTR_NAME2, ATTR_VALUE2);
			}
		});

		assertNewSession();

		setupSession();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR_NAME)).isNull();
				assertThat(wrappedRequest.getSession().getAttribute(ATTR_NAME2)).isEqualTo(ATTR_VALUE2);
			}
		});
	}

	// --- invalid session ids

	@Test
	public void doFilterGetSessionInvalidSessionId() throws Exception {
		setSessionCookie("INVALID");
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		assertNewSession();
	}

	@Test
	public void doFilterGetSessionTrueInvalidSessionId() throws Exception {
		setSessionCookie("INVALID");
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession(true);
			}
		});

		assertNewSession();
	}

	@Test
	public void doFilterGetSessionFalseInvalidSessionId() throws Exception {
		setSessionCookie("INVALID");
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession(false);
			}
		});

		assertNoSession();
	}

	// --- commit response saves immediately

	@Test
	public void doFilterSendError() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterSendErrorAndMessage() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error");
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterSendRedirect() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.sendRedirect("/");
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterFlushBuffer() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.flushBuffer();
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterOutputFlush() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getOutputStream().flush();
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterOutputClose() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getOutputStream().close();
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterWriterFlush() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getWriter().flush();
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	@Test
	public void doFilterWriterClose() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getWriter().close();
				assertThat(sessionRepository.getSession(id)).isNotNull();
			}
		});
	}

	// --- helper methods

	private void assertNewSession() {
		Cookie cookie = getSessionCookie();
		assertThat(cookie).isNotNull();
		assertThat(cookie.getMaxAge()).isEqualTo(-1);
		assertThat(cookie.getValue()).isNotEqualTo("INVALID");
		assertThat(cookie.isHttpOnly()).describedAs("Cookie is expected to be HTTP Only").isTrue();
		assertThat(cookie.getSecure()).describedAs("Cookie secured is expected to be " + request.isSecure()).isEqualTo(request.isSecure());
		assertThat(request.getSession(false)).describedAs("The original HttpServletRequest HttpSession should be null").isNull();
	}

	private void assertNoSession() {
		Cookie cookie = getSessionCookie();
		assertThat(cookie).isNull();
		assertThat(request.getSession(false)).describedAs("The original HttpServletRequest HttpSession should be null").isNull();
	}

	private Cookie getSessionCookie() {
		return response.getCookie("SESSION");
	}

	private void setSessionCookie(String sessionId) {
		request.setCookies(new Cookie[]{new Cookie("SESSION", sessionId)});
	}

	private void setupSession() {
		setSessionCookie(getSessionCookie().getValue());
	}

	private void doFilter(final DoInFilter doInFilter) throws ServletException, IOException {
		chain = new MockFilterChain(new HttpServlet() {}, new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
				doInFilter.doFilter(request, response);
			}
		});
		filter.doFilter(request, response, chain);
	}

	abstract class DoInFilter {
		void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws ServletException, IOException {
			doFilter(wrappedRequest);
		}
		void doFilter(HttpServletRequest wrappedRequest) {}
	}
}