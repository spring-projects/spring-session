/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.session.web.http;

import org.springframework.core.annotation.Order;
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Switches the {@link javax.servlet.http.HttpSession} implementation to be backed by a {@link org.springframework.session.Session}.
 *
 * The {@link SessionRepositoryFilter} wraps the {@link javax.servlet.http.HttpServletRequest} and overrides the methods
 * to get an {@link javax.servlet.http.HttpSession} to be backed by a {@link org.springframework.session.Session} returned
 * by the {@link org.springframework.session.SessionRepository}.
 *
 * The {@link SessionRepositoryFilter} uses a {@link HttpSessionStrategy} (default {@link CookieHttpSessionStrategy}  to
 * bridge logic between an {@link javax.servlet.http.HttpSession} and the {@link org.springframework.session.Session}
 * abstraction. Specifically:
 *
 * <ul>
 *     <li>The session id is looked up using {@link HttpSessionStrategy#getRequestedSessionId(javax.servlet.http.HttpServletRequest)}.
 *     The default is to look in a cookie named SESSION.</li>
 *     <li>The session id of newly created {@link org.springframework.session.ExpiringSession} is sent to the client using
 *     <li>The client is notified that the session id is no longer valid with {@link HttpSessionStrategy#onInvalidateSession(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}</li>
 * </ul>
 *
 * <p>
 * The SessionRepositoryFilter must be placed before any Filter that access the HttpSession or that might commit the response
 * to ensure the session is overridden and persisted properly.
 * </p>
 *
 * @since 1.0
 * @author Rob Winch
 */
@Order(SessionRepositoryFilter.DEFAULT_ORDER)
public class SessionRepositoryFilter<S extends ExpiringSession> extends OncePerRequestFilter {

    public static final String SESSION_REPOSITORY_ATTR = SessionRepository.class.getName();

    public static final int DEFAULT_ORDER = Integer.MIN_VALUE + 50;

    protected final SessionRepository<S> sessionRepository;

    protected ServletContext servletContext;

    protected MultiHttpSessionStrategy httpSessionStrategy = new CookieHttpSessionStrategy();

    /**
     * Creates a new instance
     *
     * @param sessionRepository the <code>SessionRepository</code> to use. Cannot be null.
     */
    public SessionRepositoryFilter(SessionRepository<S> sessionRepository) {
        if (sessionRepository == null) {
            throw new IllegalArgumentException("sessionRepository cannot be null");
        }
        this.sessionRepository = sessionRepository;
    }

    /**
     * Sets the {@link HttpSessionStrategy} to be used. The default is a {@link CookieHttpSessionStrategy}.
     *
     * @param httpSessionStrategy the {@link HttpSessionStrategy} to use. Cannot be null.
     */
    public void setHttpSessionStrategy(HttpSessionStrategy httpSessionStrategy) {
        if (httpSessionStrategy == null) {
            throw new IllegalArgumentException("httpSessionStrategy cannot be null");
        }
        this.httpSessionStrategy = new MultiHttpSessionStrategyAdapter(httpSessionStrategy);
    }

    /**
     * Sets the {@link MultiHttpSessionStrategy} to be used. The default is a {@link CookieHttpSessionStrategy}.
     *
     * @param httpSessionStrategy the {@link MultiHttpSessionStrategy} to use. Cannot be null.
     */
    public void setHttpSessionStrategy(MultiHttpSessionStrategy httpSessionStrategy) {
        if (httpSessionStrategy == null) {
            throw new IllegalArgumentException("httpSessionStrategy cannot be null");
        }
        this.httpSessionStrategy = httpSessionStrategy;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        request.setAttribute(SESSION_REPOSITORY_ATTR, sessionRepository);

        SessionRepositoryRequestWrapper wrappedRequest = wrapRequest(request, response);
        SessionRepositoryResponseWrapper wrappedResponse = wrapResponse(wrappedRequest, response);

        HttpServletRequest strategyRequest = httpSessionStrategy.wrapRequest(wrappedRequest, wrappedResponse);
        HttpServletResponse strategyResponse = httpSessionStrategy.wrapResponse(wrappedRequest, wrappedResponse);

        try {
            filterChain.doFilter(strategyRequest, strategyResponse);
        } finally {
            wrappedRequest.commitSession();
        }
    }

    protected SessionRepositoryRequestWrapper wrapRequest(HttpServletRequest request, HttpServletResponse response) {
        return new SessionRepositoryRequestWrapper(request, response, servletContext, sessionRepository, httpSessionStrategy);
    }

    protected SessionRepositoryResponseWrapper wrapResponse(SessionRepositoryRequestWrapper request, HttpServletResponse response) {
        return new SessionRepositoryResponseWrapper(request, response);
    }


    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }


    static class MultiHttpSessionStrategyAdapter implements MultiHttpSessionStrategy {
        private HttpSessionStrategy delegate;

        public MultiHttpSessionStrategyAdapter(HttpSessionStrategy delegate) {
            this.delegate = delegate;
        }

        public String getRequestedSessionId(HttpServletRequest request) {
            return delegate.getRequestedSessionId(request);
        }

        public void onNewSession(Session session, HttpServletRequest request,
                                 HttpServletResponse response) {
            delegate.onNewSession(session, request, response);
        }

        public void onInvalidateSession(HttpServletRequest request,
                                        HttpServletResponse response) {
            delegate.onInvalidateSession(request, response);
        }

        public HttpServletRequest wrapRequest(HttpServletRequest request,
                                              HttpServletResponse response) {
            return request;
        }

        public HttpServletResponse wrapResponse(HttpServletRequest request,
                                                HttpServletResponse response) {
            return response;
        }
    }
}
