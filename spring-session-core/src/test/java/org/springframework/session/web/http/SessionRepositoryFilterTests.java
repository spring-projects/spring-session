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

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;

import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link SessionRepositoryFilter}.
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("deprecation")
public class SessionRepositoryFilterTests {

	@Mock
	private HttpSessionIdResolver strategy;

	private Map<String, Session> sessions;

	private SessionRepository<MapSession> sessionRepository;

	private SessionRepositoryFilter<MapSession> filter;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	private MockFilterChain chain;

	@Before
	public void setup() throws Exception {
		this.sessions = new HashMap<>();
		this.sessionRepository = new MapSessionRepository(this.sessions);
		this.filter = new SessionRepositoryFilter<>(this.sessionRepository);
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
				SessionRepositoryFilterTests.this.request.setAttribute(CREATE_ATTR,
						creationTime);
			}
		});

		final long expectedCreationTime = (Long) this.request.getAttribute(CREATE_ATTR);
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
	public void doFilterCreateSetsLastAccessedTime() throws Exception {
		MapSession session = new MapSession();
		session.setLastAccessedTime(Instant.EPOCH);
		this.sessionRepository = spy(this.sessionRepository);
		given(this.sessionRepository.createSession()).willReturn(session);
		this.filter = new SessionRepositoryFilter<>(this.sessionRepository);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				long now = System.currentTimeMillis();
				long fiveSecondsAgo = now - TimeUnit.SECONDS.toMillis(5);
				assertThat(session.getLastAccessedTime()).isLessThanOrEqualTo(now);
				assertThat(session.getLastAccessedTime())
						.isGreaterThanOrEqualTo(fiveSecondsAgo);
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
				assertThat(lastAccessed).isCloseTo(
						wrappedRequest.getSession().getCreationTime(), Offset.offset(5L));
				SessionRepositoryFilterTests.this.request.setAttribute(ACCESS_ATTR,
						lastAccessed);
			}
		});

		Thread.sleep(50L);
		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				long lastAccessed = wrappedRequest.getSession().getLastAccessedTime();

				assertThat(lastAccessed)
						.isGreaterThan(wrappedRequest.getSession().getCreationTime());
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
				SessionRepositoryFilterTests.this.request.setAttribute(ID_ATTR, id);
			}
		});

		final String id = (String) this.request.getAttribute(ID_ATTR);
		assertThat(base64Decode(getSessionCookie().getValue())).isEqualTo(id);
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
				SessionRepositoryFilterTests.this.request.setAttribute(ID_ATTR, id);
			}
		});

		final String id = (String) this.request.getAttribute(ID_ATTR);
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
		this.filter = new SessionRepositoryFilter<>(this.sessionRepository);
		this.filter.setServletContext(expectedContext);

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
				// 30 minute default (same as
				// Tomcat)
				assertThat(interval).isEqualTo(1800);
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
				assertThat(wrappedRequest.getSession().getMaxInactiveInterval())
						.isEqualTo(interval);
			}
		});

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getMaxInactiveInterval())
						.isEqualTo(interval);
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
				assertThat(wrappedRequest.getSession().getAttribute(ATTR))
						.isEqualTo(VALUE);
				assertThat(
						Collections.list(wrappedRequest.getSession().getAttributeNames()))
								.containsOnly(ATTR);
			}
		});

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR))
						.isEqualTo(VALUE);
				assertThat(
						Collections.list(wrappedRequest.getSession().getAttributeNames()))
								.containsOnly(ATTR);
			}
		});

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR))
						.isEqualTo(VALUE);

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
				assertThat(Arrays.asList(wrappedRequest.getSession().getValueNames()))
						.containsOnly(ATTR);
			}
		});

		nextRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getValue(ATTR)).isEqualTo(VALUE);
				assertThat(Arrays.asList(wrappedRequest.getSession().getValueNames()))
						.containsOnly(ATTR);
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
		this.response.reset();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().isNew()).isFalse();
			}
		});

		assertThat(this.response.getCookie("SESSION")).isNull();
	}

	@Test
	public void doFilterSetsCookieIfChanged() throws Exception {
		this.sessionRepository = new MapSessionRepository(new ConcurrentHashMap<>()) {
			@Override
			public MapSession findById(String id) {
				return createSession();
			}
		};
		this.filter = new SessionRepositoryFilter<>(this.sessionRepository);
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});
		assertThat(this.response.getCookie("SESSION")).isNotNull();

		nextRequest();

		this.response.reset();
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().isNew()).isFalse();
			}
		});

		assertThat(this.response.getCookie("SESSION")).isNotNull();
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
		this.request.setRequestedSessionIdValid(false);

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

		final String originalSessionId = base64Decode(getSessionCookie().getValue());
		nextRequest();

		// change the session id
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession originalSession = wrappedRequest.getSession();
				assertThat(originalSession.getId()).isEqualTo(originalSessionId);

				String changeSessionId = ReflectionTestUtils.invokeMethod(wrappedRequest,
						"changeSessionId");
				assertThat(changeSessionId).isNotEqualTo(originalSessionId);
				// gh-227
				assertThat(originalSession.getId()).isEqualTo(changeSessionId);
			}
		});

		// the old session was removed
		final String changedSessionId = getSessionCookie().getValue();
		assertThat(originalSessionId).isNotEqualTo(changedSessionId);
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();

		nextRequest();

		// The attributes from previous session were migrated
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.getSession().getAttribute(ATTR))
						.isEqualTo(VALUE);
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
				}
				catch (IllegalStateException ignored) {
				}
			}
		});
	}

	// gh-142, gh-153
	@Test
	public void doFilterIsRequestedValidSessionFalseInvalidId() throws Exception {
		setSessionCookie("invalid");
		this.request.setRequestedSessionIdValid(true);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				assertThat(wrappedRequest.isRequestedSessionIdValid()).isFalse();
			}
		});
	}

	@Test
	public void doFilterIsRequestedValidSessionFalse() throws Exception {
		this.request.setRequestedSessionIdValid(true);

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
		this.request.setSecure(true);
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				wrappedRequest.getSession();
			}
		});

		Cookie session = getSessionCookie();
		assertThat(session.isHttpOnly()).describedAs("Session Cookie should be HttpOnly")
				.isTrue();
		assertThat(session.getSecure())
				.describedAs("Session Cookie should be marked as Secure").isTrue();
	}

	@Test
	public void doFilterSessionContext() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSessionContext sessionContext = wrappedRequest.getSession()
						.getSessionContext();
				assertThat(sessionContext).isNotNull();
				assertThat(sessionContext.getSession("a")).isNull();
				assertThat(sessionContext.getIds()).isNotNull();
				assertThat(sessionContext.getIds().hasMoreElements()).isFalse();

				try {
					sessionContext.getIds().nextElement();
					fail("Expected Exception");
				}
				catch (NoSuchElementException ignored) {
				}
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
				assertThat(wrappedRequest.getSession().getAttribute(ATTR_NAME))
						.isEqualTo(ATTR_VALUE);
				assertThat(wrappedRequest.getSession().getAttribute(ATTR_NAME2))
						.isEqualTo(ATTR_VALUE2);
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				}
				catch (IllegalStateException ignored) {
				}
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
				assertThat(wrappedRequest.getSession().getAttribute(ATTR_NAME2))
						.isEqualTo(ATTR_VALUE2);
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
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	@Test
	public void doFilterSendErrorAndMessage() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"Error");
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	@Test
	public void doFilterSendRedirect() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.sendRedirect("/");
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	@Test
	public void doFilterFlushBuffer() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.flushBuffer();
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	@Test
	public void doFilterOutputFlush() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getOutputStream().flush();
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	@Test
	public void doFilterOutputClose() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getOutputStream().close();
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	@Test
	public void doFilterWriterFlush() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getWriter().flush();
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	@Test
	public void doFilterWriterClose() throws Exception {
		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String id = wrappedRequest.getSession().getId();
				wrappedResponse.getWriter().close();
				assertThat(
						SessionRepositoryFilterTests.this.sessionRepository.findById(id))
								.isNotNull();
			}
		});
	}

	// --- HttpSessionIdResolver

	@Test
	public void doFilterAdapterGetRequestedSessionId() throws Exception {
		SessionRepository<MapSession> sessionRepository = spy(
				new MapSessionRepository(new ConcurrentHashMap<>()));

		this.filter = new SessionRepositoryFilter<>(sessionRepository);
		this.filter.setHttpSessionIdResolver(this.strategy);
		final String expectedId = "HttpSessionIdResolver-requested-id";

		given(this.strategy.resolveSessionIds(any(HttpServletRequest.class)))
				.willReturn(Collections.singletonList(expectedId));
		given(sessionRepository.findById(anyString()))
				.willReturn(new MapSession(expectedId));

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				String actualId = wrappedRequest.getRequestedSessionId();
				assertThat(actualId).isEqualTo(expectedId);
			}
		});
	}

	@Test
	public void doFilterAdapterOnNewSession() throws Exception {
		this.filter.setHttpSessionIdResolver(this.strategy);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession();
			}
		});

		HttpServletRequest request = (HttpServletRequest) this.chain.getRequest();
		Session session = this.sessionRepository.findById(request.getSession().getId());
		verify(this.strategy).setSessionId(any(HttpServletRequest.class),
				any(HttpServletResponse.class), eq(session.getId()));
	}

	@Test
	public void doFilterAdapterOnInvalidate() throws Exception {
		this.filter.setHttpSessionIdResolver(this.strategy);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().getId();
			}
		});

		HttpServletRequest request = (HttpServletRequest) this.chain.getRequest();
		String id = request.getSession().getId();
		given(this.strategy.resolveSessionIds(any(HttpServletRequest.class)))
				.willReturn(Collections.singletonList(id));
		setupRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().invalidate();
			}
		});

		verify(this.strategy).expireSession(any(HttpServletRequest.class),
				any(HttpServletResponse.class));
	}

	// gh-188
	@Test
	public void doFilterRequestSessionNoRequestSessionDoesNotInvalidate()
			throws Exception {
		this.filter.setHttpSessionIdResolver(this.strategy);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().getId();
			}
		});

		HttpServletRequest request = (HttpServletRequest) this.chain.getRequest();
		String id = request.getSession().getId();
		given(this.strategy.resolveSessionIds(any(HttpServletRequest.class)))
				.willReturn(Collections.singletonList(id));

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verify(this.strategy, never()).expireSession(any(HttpServletRequest.class),
				any(HttpServletResponse.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doFilterRequestSessionNoRequestSessionNoSessionRepositoryInteractions()
			throws Exception {
		SessionRepository<MapSession> sessionRepository = spy(
				new MapSessionRepository(new ConcurrentHashMap<>()));

		this.filter = new SessionRepositoryFilter<>(sessionRepository);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
				wrappedRequest.getSession().getId();
			}
		});

		reset(sessionRepository);
		setupRequest();

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verifyZeroInteractions(sessionRepository);
	}

	@Test
	public void doFilterLazySessionCreation() throws Exception {
		SessionRepository<MapSession> sessionRepository = spy(
				new MapSessionRepository(new ConcurrentHashMap<>()));

		this.filter = new SessionRepositoryFilter<>(sessionRepository);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verifyZeroInteractions(sessionRepository);
	}

	@Test
	public void doFilterLazySessionUpdates() throws Exception {
		MapSession session = this.sessionRepository.createSession();
		this.sessionRepository.save(session);
		SessionRepository<MapSession> sessionRepository = spy(this.sessionRepository);
		setSessionCookie(session.getId());

		this.filter = new SessionRepositoryFilter<>(sessionRepository);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) throws IOException {
			}
		});

		verifyZeroInteractions(sessionRepository);
	}

	@Test
	public void doFilterSessionRetrievalIsCached() throws Exception {
		MapSession session = this.sessionRepository.createSession();
		this.sessionRepository.save(session);
		SessionRepository<MapSession> sessionRepository = spy(this.sessionRepository);
		setSessionCookie(session.getId());

		this.filter = new SessionRepositoryFilter<>(sessionRepository);

		doFilter(new DoInFilter() {
			@Override
			public void doFilter(HttpServletRequest wrappedRequest,
					HttpServletResponse wrappedResponse) {
				wrappedRequest.getSession().invalidate();
				wrappedRequest.getSession();
			}
		});

		// 3 invocations expected: initial resolution, after invalidation, after commit
		verify(sessionRepository, times(3)).findById(eq(session.getId()));
		verify(sessionRepository).deleteById(eq(session.getId()));
		verify(sessionRepository).createSession();
		verify(sessionRepository).save(any());
		verifyZeroInteractions(sessionRepository);
	}

	// --- order

	@Test
	public void order() {
		assertThat(AnnotationAwareOrderComparator.INSTANCE.compare(this.filter,
				new SessionRepositoryFilterDefaultOrder()));
	}

	// We want the filter to work without any dependencies on Spring
	@Test
	@SuppressWarnings("unused")
	public void doesNotImplementOrdered() {
		assertThatThrownBy(() -> {
			Ordered o = (Ordered) this.filter;
		}).isInstanceOf(ClassCastException.class);
	}

	@Test
	public void setHttpSessionIdResolverNull() {
		assertThatThrownBy(() -> this.filter.setHttpSessionIdResolver(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("httpSessionIdResolver cannot be null");
	}

	@Test
	public void bindingListenerBindListener() throws Exception {
		String bindingListenerName = "bindingListener";
		CountingHttpSessionBindingListener bindingListener = new CountingHttpSessionBindingListener();

		doFilter(new DoInFilter() {

			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.setAttribute(bindingListenerName, bindingListener);
			}

		});

		assertThat(bindingListener.getCounter()).isEqualTo(1);
	}

	@Test
	public void bindingListenerBindListenerThenUnbind() throws Exception {
		String bindingListenerName = "bindingListener";
		CountingHttpSessionBindingListener bindingListener = new CountingHttpSessionBindingListener();

		doFilter(new DoInFilter() {

			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.setAttribute(bindingListenerName, bindingListener);
				session.removeAttribute(bindingListenerName);
			}

		});

		assertThat(bindingListener.getCounter()).isEqualTo(0);
	}

	@Test
	public void bindingListenerBindSameListenerTwice() throws Exception {
		String bindingListenerName = "bindingListener";
		CountingHttpSessionBindingListener bindingListener = new CountingHttpSessionBindingListener();

		doFilter(new DoInFilter() {

			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.setAttribute(bindingListenerName, bindingListener);
				session.setAttribute(bindingListenerName, bindingListener);
			}

		});

		assertThat(bindingListener.getCounter()).isEqualTo(1);
	}

	@Test
	public void bindingListenerBindListenerOverwrite() throws Exception {
		String bindingListenerName = "bindingListener";
		CountingHttpSessionBindingListener bindingListener1 = new CountingHttpSessionBindingListener();
		CountingHttpSessionBindingListener bindingListener2 = new CountingHttpSessionBindingListener();

		doFilter(new DoInFilter() {

			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.setAttribute(bindingListenerName, bindingListener1);
				session.setAttribute(bindingListenerName, bindingListener2);
			}

		});

		assertThat(bindingListener1.getCounter()).isEqualTo(0);
		assertThat(bindingListener2.getCounter()).isEqualTo(1);
	}

	@Test
	public void bindingListenerBindThrowsException() throws Exception {
		String bindingListenerName = "bindingListener";
		CountingHttpSessionBindingListener bindingListener = new CountingHttpSessionBindingListener();

		doFilter(new DoInFilter() {

			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				bindingListener.setThrowException();
				session.setAttribute(bindingListenerName, bindingListener);
			}

		});

		assertThat(bindingListener.getCounter()).isEqualTo(0);
	}

	@Test
	public void bindingListenerBindListenerThenUnbindThrowsException() throws Exception {
		String bindingListenerName = "bindingListener";
		CountingHttpSessionBindingListener bindingListener = new CountingHttpSessionBindingListener();

		doFilter(new DoInFilter() {

			@Override
			public void doFilter(HttpServletRequest wrappedRequest) {
				HttpSession session = wrappedRequest.getSession();
				session.setAttribute(bindingListenerName, bindingListener);
				bindingListener.setThrowException();
				session.removeAttribute(bindingListenerName);
			}

		});

		assertThat(bindingListener.getCounter()).isEqualTo(1);
	}

	// --- helper methods

	private void assertNewSession() {
		Cookie cookie = getSessionCookie();
		assertThat(cookie).isNotNull();
		assertThat(cookie.getMaxAge()).isEqualTo(-1);
		assertThat(cookie.getValue()).isNotEqualTo("INVALID");
		assertThat(cookie.isHttpOnly()).describedAs("Cookie is expected to be HTTP Only")
				.isTrue();
		assertThat(cookie.getSecure())
				.describedAs(
						"Cookie secured is expected to be " + this.request.isSecure())
				.isEqualTo(this.request.isSecure());
		assertThat(this.request.getSession(false))
				.describedAs("The original HttpServletRequest HttpSession should be null")
				.isNull();
	}

	private void assertNoSession() {
		Cookie cookie = getSessionCookie();
		assertThat(cookie).isNull();
		assertThat(this.request.getSession(false))
				.describedAs("The original HttpServletRequest HttpSession should be null")
				.isNull();
	}

	private Cookie getSessionCookie() {
		return this.response.getCookie("SESSION");
	}

	private void setSessionCookie(String sessionId) {
		this.request.setCookies(new Cookie("SESSION", base64Encode(sessionId)));
	}

	private void setupRequest() {
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.chain = new MockFilterChain();
	}

	private void nextRequest() throws Exception {
		Map<String, Cookie> nameToCookie = new HashMap<>();
		if (this.request.getCookies() != null) {
			for (Cookie cookie : this.request.getCookies()) {
				nameToCookie.put(cookie.getName(), cookie);
			}
		}
		for (Cookie cookie : this.response.getCookies()) {
			nameToCookie.put(cookie.getName(), cookie);
		}
		Cookie[] nextRequestCookies = new ArrayList<>(nameToCookie.values())
				.toArray(new Cookie[0]);

		setupRequest();

		this.request.setCookies(nextRequestCookies);
	}

	private void doFilter(final DoInFilter doInFilter)
			throws ServletException, IOException {
		this.chain = new MockFilterChain(new HttpServlet() {
		}, new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request,
					HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException {
				doInFilter.doFilter(request, response);
			}
		});
		this.filter.doFilter(this.request, this.response, this.chain);
	}

	private static String base64Encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes());
	}

	private static String base64Decode(String value) {
		return new String(Base64.getDecoder().decode(value));
	}

	private static class SessionRepositoryFilterDefaultOrder implements Ordered {

		@Override
		public int getOrder() {
			return SessionRepositoryFilter.DEFAULT_ORDER;
		}

	}

	private abstract class DoInFilter {

		void doFilter(HttpServletRequest wrappedRequest,
				HttpServletResponse wrappedResponse)
				throws ServletException, IOException {
			doFilter(wrappedRequest);
		}

		void doFilter(HttpServletRequest wrappedRequest) {
		}

	}

	private static class CountingHttpSessionBindingListener
			implements HttpSessionBindingListener {

		private final AtomicInteger counter = new AtomicInteger(0);

		private final AtomicBoolean throwException = new AtomicBoolean(false);

		@Override
		public void valueBound(HttpSessionBindingEvent event) {
			if (this.throwException.get()) {
				this.throwException.compareAndSet(true, false);
				throw new RuntimeException("bind exception");
			}
			this.counter.incrementAndGet();
		}

		@Override
		public void valueUnbound(HttpSessionBindingEvent event) {
			if (this.throwException.get()) {
				this.throwException.compareAndSet(true, false);
				throw new RuntimeException("unbind exception");
			}
			this.counter.decrementAndGet();
		}

		int getCounter() {
			return this.counter.get();
		}

		void setThrowException() {
			this.throwException.compareAndSet(false, true);
		}

	}

}
