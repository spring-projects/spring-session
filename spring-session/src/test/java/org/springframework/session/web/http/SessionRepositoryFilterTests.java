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

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("deprecation")
public class SessionRepositoryFilterTests {
	@Mock
	private HttpSessionStrategy strategy;

	private Map<String, ExpiringSession> sessions;

	private SessionRepository<ExpiringSession> sessionRepository;

	private SessionRepositoryFilter<ExpiringSession> filter;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private MockFilterChain chain;

	@Before
	public void setup() throws Exception {
		sessions = new HashMap<String, ExpiringSession>();
		sessionRepository = new MapSessionRepository(sessions);
		filter = new SessionRepositoryFilter<ExpiringSession>(sessionRepository);
		setupRequest();
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
		nextRequest();

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

		Thread.sleep(1L);
		nextRequest();

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
		setupRequest();

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

	// gh-111
	@Test
	public void doFilterServletContextExplicit() throws Exception {
		final ServletContext expectedContext = new MockServletContext();
		filter = new SessionRepositoryFilter<ExpiringSession>(sessionRepository);
		filter.setServletContext(expectedContext);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				ServletContext context = wrappedRequest.getSession().getServletContext();
				assertThat(context).isSameAs(expectedContext);
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

		nextRequest();

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

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isEqualTo(VALUE);
				assertThat(Collections.list(wrappedRequest.getSession().getAttributeNames())).containsOnly(ATTR);
			}
		});

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isEqualTo(VALUE);

				wrappedRequest.getSession().removeAttribute(ATTR);

				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isNull();
			}
		});

		nextRequest();

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

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getValue(ATTR)).isEqualTo(VALUE);
				assertThat(Arrays.asList(wrappedRequest.getSession().getValueNames())).containsOnly(ATTR);
			}
		});

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getValue(ATTR)).isEqualTo(VALUE);

				wrappedRequest.getSession().removeValue(ATTR);

				assertThat(wrappedRequest.getSession().getValue(ATTR)).isNull();
			}
		});

		nextRequest();

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

		nextRequest();
		response.reset();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().isNew()).isFalse();
			}
		});

		assertThat(response.getCookie("SESSION")).isNull();
	}

	@Test
	public void doFilterSetsCookieIfChanged() throws Exception {
		sessionRepository = new MapSessionRepository() {
			@Override
			public ExpiringSession getSession(String id) {
				return createSession();
			}
		};
		filter = new SessionRepositoryFilter<ExpiringSession>(sessionRepository);
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});
		assertThat(response.getCookie("SESSION")).isNotNull();

		nextRequest();

		response.reset();
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().isNew()).isFalse();
			}
		});

		assertThat(response.getCookie("SESSION")).isNotNull();
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
	public void doFilterIsRequestedValidSessionTrue() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		nextRequest();
		request.setRequestedSessionIdValid(false); // ensure we are using wrapped request

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.isRequestedSessionIdValid()).isTrue();
			}
		});
	}

	// gh-152
	@Test
	public void doFilterChangeSessionId() throws Exception {
		final String ATTR = "ATTRIBUTE";
		final String VALUE = "VALUE";

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession().setAttribute(ATTR, VALUE);
			}
		});

		final String originalSessionId = getSessionCookie().getValue();
		nextRequest();

		// change the session id
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession originalSession = wrappedRequest.getSession();
				assertThat(originalSession.getId()).isEqualTo(originalSessionId);

				String changeSessionId = ReflectionTestUtils.invokeMethod(wrappedRequest, "changeSessionId");
				assertThat(changeSessionId).isNotEqualTo(originalSessionId);
				// gh-227
				assertThat(originalSession.getId()).isEqualTo(changeSessionId);
			}
		});

		// the old session was removed
		final String changedSessionId = getSessionCookie().getValue();
		assertThat(originalSessionId).isNotEqualTo(changedSessionId);
		assertThat(sessionRepository.getSession(originalSessionId)).isNull();

		nextRequest();

		// The attributes from previous session were migrated
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR)).isEqualTo(VALUE);
			}
		});
	}

	@Test
	public void doFilterChangeSessionIdNoSession() throws Exception {
		// change the session id
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				try {
					ReflectionTestUtils.invokeMethod(wrappedRequest, "changeSessionId");
					fail("Exected Exception");
				} catch(IllegalStateException success) {}
			}
		});
	}

	// gh-142, gh-153
	@Test
	public void doFilterIsRequestedValidSessionFalseInvalidId() throws Exception {
		setSessionCookie("invalid");
		request.setRequestedSessionIdValid(true); // ensure we are using wrapped request

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.isRequestedSessionIdValid()).isFalse();
			}
		});
	}

	@Test
	public void doFilterIsRequestedValidSessionFalse() throws Exception {
		request.setRequestedSessionIdValid(true); // ensure we are using wrapped request

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.isRequestedSessionIdValid()).isFalse();
			}
		});
	}

	@Test
	public void doFilterGetSessionGetSessionFalse() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession(false)).isNotNull();
			}
		});
	}

	// gh-229
	@Test
	public void doFilterGetSessionGetSessionOnError() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession().setAttribute("a", "b");
			}
		});

		// reuse the same request similar to processing an ERROR dispatch

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession(false)).isNotNull();
			}
		});

		assertThat(this.sessions.size()).isEqualTo(1);
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

		nextRequest();

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

		nextRequest();

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

	// --- MultiHttpSessionStrategyAdapter

	@Test
	public void doFilterAdapterGetRequestedSessionId() throws Exception {
		filter.setHttpSessionStrategy(strategy);
		final String expectedId = "MultiHttpSessionStrategyAdapter-requested-id";
		when(strategy.getRequestedSessionId(any(HttpServletRequest.class))).thenReturn(expectedId);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				String actualId = wrappedRequest.getRequestedSessionId();
				assertThat(actualId).isEqualTo(expectedId);
			}
		});
	}

	@Test
	public void doFilterAdapterOnNewSession() throws Exception {
		filter.setHttpSessionStrategy(strategy);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession();
			}
		});

		HttpServletRequest request = (HttpServletRequest) chain.getRequest();
		Session session = sessionRepository.getSession(request.getSession().getId());
		verify(strategy).onNewSession(eq(session), any(HttpServletRequest.class),any(HttpServletResponse.class));
	}

	@Test
	public void doFilterAdapterOnInvalidate() throws Exception {
		filter.setHttpSessionStrategy(strategy);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().getId();
			}
		});

		HttpServletRequest request = (HttpServletRequest) chain.getRequest();
		String id = request.getSession().getId();
		when(strategy.getRequestedSessionId(any(HttpServletRequest.class))).thenReturn(id);
		setupRequest();

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().invalidate();
			}
		});

		verify(strategy).onInvalidateSession(any(HttpServletRequest.class),any(HttpServletResponse.class));
	}

	// gh-188
	@Test
	public void doFilterRequestSessionNoRequestSessionDoesNotInvalidate() throws Exception {
		filter.setHttpSessionStrategy(strategy);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().getId();
			}
		});

		HttpServletRequest request = (HttpServletRequest) chain.getRequest();
		String id = request.getSession().getId();
		when(strategy.getRequestedSessionId(any(HttpServletRequest.class))).thenReturn(id);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verify(strategy,never()).onInvalidateSession(any(HttpServletRequest.class),any(HttpServletResponse.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doFilterRequestSessionNoRequestSessionNoSessionRepositoryInteractions() throws Exception {
		SessionRepository<ExpiringSession> sessionRepository = spy(new MapSessionRepository());

		filter = new SessionRepositoryFilter<ExpiringSession>(sessionRepository);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().getId();
			}
		});

		reset(sessionRepository);
		setupRequest();

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verifyZeroInteractions(sessionRepository);
	}

	@Test
	public void doFilterLazySessionCreation() throws Exception {
		SessionRepository<ExpiringSession> sessionRepository = spy(new MapSessionRepository());

		filter = new SessionRepositoryFilter<ExpiringSession>(sessionRepository);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verifyZeroInteractions(sessionRepository);
	}

	@Test
	public void doFilterLazySessionUpdates() throws Exception {
		ExpiringSession session = this.sessionRepository.createSession();
		this.sessionRepository.save(session);
		SessionRepository<ExpiringSession> sessionRepository = spy(this.sessionRepository);
		setSessionCookie(session.getId());

		filter = new SessionRepositoryFilter<ExpiringSession>(sessionRepository);

		doFilter(new DoInFilter(){
			@Override
			public void doFilter(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verifyZeroInteractions(sessionRepository);
	}

	// --- order

	@Test
	public void order() {
		assertThat(AnnotationAwareOrderComparator.INSTANCE.compare(filter, new SessionRepositoryFilterDefaultOrder()));
	}

	// We want the filter to work without any dependencies on Spring
	@Test(expected = ClassCastException.class)
	@SuppressWarnings("unused")
	public void doesNotImplementOrdered() {
		Ordered o = (Ordered) filter;
	}

	@Test(expected = IllegalArgumentException.class)
	public void setHttpSessionStrategyNull() {
		filter.setHttpSessionStrategy((HttpSessionStrategy) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setMultiHttpSessionStrategyNull() {
		filter.setHttpSessionStrategy((MultiHttpSessionStrategy) null);
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

	private void setupRequest() {
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		chain = new MockFilterChain();
	}

	private void nextRequest() throws Exception {
		Map<String,Cookie> nameToCookie = new HashMap<String,Cookie>();
		if (request.getCookies() != null) {
			for(Cookie cookie : request.getCookies()) {
				nameToCookie.put(cookie.getName(), cookie);
			}
		}
		if (response.getCookies() != null) {
			for(Cookie cookie : response.getCookies()) {
				nameToCookie.put(cookie.getName(), cookie);
			}
		}
		Cookie[] nextRequestCookies = new ArrayList<Cookie>(nameToCookie.values()).toArray(new Cookie[0]);

		setupRequest();

		request.setCookies(nextRequestCookies);
	}

	@SuppressWarnings("serial")
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

	static class SessionRepositoryFilterDefaultOrder implements Ordered {
		public int getOrder() {
			return SessionRepositoryFilter.DEFAULT_ORDER;
		}
	}
}