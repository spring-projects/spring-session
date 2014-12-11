/*
 * Copyright 2002-2014 the original author or authors.
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.springframework.session.Session;

/**
 * A {@link HttpSessionStrategy} that uses a cookie to obtain the session from.
 * Specifically, this implementation will allow specifying a cookie name using
 * {@link CookieHttpSessionStrategy#setCookieName(String)}. The default is
 * "SESSION".
 *
 * When a session is created, the HTTP response will have a cookie with the
 * specified cookie name and the value of the session id. The cookie will be
 * marked as a session cookie, use the context path for the path of the cookie,
 * marked as HTTPOnly, and if
 * {@link javax.servlet.http.HttpServletRequest#isSecure()} returns true, the
 * cookie will be marked as secure. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Path=/context-root; Secure; HttpOnly
 * </pre>
 *
 * The client should now include the session in each request by specifying the
 * same cookie in their request. For example:
 *
 * <pre>
 * GET /messages/ HTTP/1.1
 * Host: example.com
 * Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 * </pre>
 *
 * When the session is invalidated, the server will send an HTTP response that
 * expires the cookie. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Expires=Thur, 1 Jan 1970 00:00:00 GMT; Secure; HttpOnly
 * </pre>
 *
 * <h2>Supporting Multiple Simultaneous Sessions</h2>
 *
 * <p>
 * By default multiple sessions are also supported. Once a session is
 * established with the browser, another session can be initiated by specifying
 * a unique value for the {@link #setSessionAliasParamName(String)}. For
 * example, a request to:
 * </p>
 *
 * <pre>
 * GET /messages/?_s=1416195761178 HTTP/1.1
 * Host: example.com
 * Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 * </pre>
 *
 * Will result in the following response:
 *
 * <pre>
 *  HTTP/1.1 200 OK
 * Set-Cookie: SESSION="0 f81d4fae-7dec-11d0-a765-00a0c91e6bf6 1416195761178 8a929cde-2218-4557-8d4e-82a79a37876d"; Expires=Thur, 1 Jan 1970 00:00:00 GMT; Secure; HttpOnly
 * </pre>
 *
 * <p>
 * To use the original session a request without the HTTP parameter u can be
 * made. To use the new session, a request with the HTTP parameter
 * _s=1416195761178 can be used. By default URLs will be rewritten to include the
 * currently selected session.
 * </p>
 *
 * <h2>Selecting Sessions</h2>
 *
 * <p>
 * Sessions can be managed by using the HttpSessionManager and
 * SessionRepository. If you are not using Spring in the rest of your
 * application you can obtain a reference from the HttpServletRequest
 * attributes. An example is provided below:
 * </p>
 *
 * <code>
 * HttpSessionManager sessionManager =
 *              (HttpSessionManager) req.getAttribute(HttpSessionManager.class.getName());
 *      SessionRepository<Session> repo =
 *              (SessionRepository<Session>) req.getAttribute(SessionRepository.class.getName());
 *
 *      String currentSessionAlias = sessionManager.getCurrentSessionAlias(req);
 *      Map<String, String> sessionIds = sessionManager.getSessionIds(req);
 *      String newSessionAlias = String.valueOf(System.currentTimeMillis());
 *
 *      String contextPath = req.getContextPath();
 *      List<Account> accounts = new ArrayList<>();
 *      Account currentAccount = null;
 *      for(Map.Entry<String, String> entry : sessionIds.entrySet()) {
 *          String alias = entry.getKey();
 *          String sessionId = entry.getValue();
 *
 *          Session session = repo.getSession(sessionId);
 *          if(session == null) {
 *              continue;
 *          }
 *
 *          String username = session.getAttribute("username");
 *          if(username == null) {
 *              newSessionAlias = alias;
 *              continue;
 *          }
 *
 *          String logoutUrl = sessionManager.encodeURL("./logout", alias);
 *          String switchAccountUrl = sessionManager.encodeURL("./", alias);
 *          Account account = new Account(username, logoutUrl, switchAccountUrl);
 *          if(currentSessionAlias.equals(alias)) {
 *              currentAccount = account;
 *          } else {
 *              accounts.add(account);
 *          }
 *      }
 *
 *      req.setAttribute("currentAccount", currentAccount);
 *      req.setAttribute("addAccountUrl", sessionManager.encodeURL(contextPath, newSessionAlias));
 *      req.setAttribute("accounts", accounts);
 * </code>
 *
 *
 * @since 1.0
 * @author Rob Winch
 */
public final class CookieHttpSessionStrategy implements MultiHttpSessionStrategy, HttpSessionManager {
    static final String DEFAULT_ALIAS = "0";

    static final String DEFAULT_SESSION_ALIAS_PARAM_NAME = "_s";

    private String cookieName = "SESSION";

    private String sessionParam = DEFAULT_SESSION_ALIAS_PARAM_NAME;

    @Override
    public String getRequestedSessionId(HttpServletRequest request) {
        Map<String,String> sessionIds = getSessionIds(request);
        String sessionAlias = getCurrentSessionAlias(request);
        return sessionIds.get(sessionAlias);
    }

    @Override
    public String getCurrentSessionAlias(HttpServletRequest request) {
        if(sessionParam == null) {
            return DEFAULT_ALIAS;
        }
        String u = request.getParameter(sessionParam);
        if(u == null) {
            return DEFAULT_ALIAS;
        }
        return u;
    }

    @Override
    public void onNewSession(Session session, HttpServletRequest request, HttpServletResponse response) {
        Map<String,String> sessionIds = getSessionIds(request);
        String sessionAlias = getCurrentSessionAlias(request);
        sessionIds.put(sessionAlias, session.getId());
        Cookie sessionCookie = createSessionCookie(request, sessionIds);
        response.addCookie(sessionCookie);
    }

    private Cookie createSessionCookie(HttpServletRequest request,
            Map<String, String> sessionIds) {
        Cookie sessionCookie = new Cookie(cookieName,"");
        sessionCookie.setHttpOnly(true);
        sessionCookie.setSecure(request.isSecure());
        sessionCookie.setPath(cookiePath(request));
        // TODO set domain?

        if(sessionIds.isEmpty()) {
            sessionCookie.setMaxAge(0);
            return sessionCookie;
        }

        if(sessionIds.size() == 1) {
            String cookieValue = sessionIds.values().iterator().next();
            sessionCookie.setValue(cookieValue);
            return sessionCookie;
        }
        StringBuffer buffer = new StringBuffer();
        for(Map.Entry<String,String> entry : sessionIds.entrySet()) {
            String alias = entry.getKey();
            String id = entry.getValue();

            buffer.append(alias);
            buffer.append(" ");
            buffer.append(id);
            buffer.append(" ");
        }
        buffer.deleteCharAt(buffer.length()-1);

        sessionCookie.setValue(buffer.toString());
        return sessionCookie;
    }

    @Override
    public void onInvalidateSession(HttpServletRequest request, HttpServletResponse response) {
        Map<String,String> sessionIds = getSessionIds(request);
        String requestedAlias = getCurrentSessionAlias(request);
        sessionIds.remove(requestedAlias);

        Cookie sessionCookie = createSessionCookie(request, sessionIds);
        response.addCookie(sessionCookie);
    }

    /**
     * Sets the name of the HTTP parameter that is used to specify the session
     * alias. If the value is null, then only a single session is supported per
     * browser.
     *
     * @param sessionAliasParamName
     *            the name of the HTTP parameter used to specify the session
     *            alias. If null, then ony a single session is supported per
     *            browser.
     */
    public void setSessionAliasParamName(String sessionAliasParamName) {
        this.sessionParam = sessionAliasParamName;
    }

    /**
     * Sets the name of the cookie to be used
     * @param cookieName
     */
    public void setCookieName(String cookieName) {
        if(cookieName == null) {
            throw new IllegalArgumentException("cookieName cannot be null");
        }
        this.cookieName = cookieName;
    }

    /**
     * Retrieve the first cookie with the given name. Note that multiple
     * cookies can have the same name but different paths or domains.
     * @param request current servlet request
     * @param name cookie name
     * @return the first cookie with the given name, or {@code null} if none is found
     */
    private static Cookie getCookie(HttpServletRequest request, String name) {
        if(request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie;
                }
            }
        }
        return null;
    }

    private static String cookiePath(HttpServletRequest request) {
        return request.getContextPath() + "/";
    }

    @Override
    public Map<String,String> getSessionIds(HttpServletRequest request) {
        Cookie session = getCookie(request, cookieName);
        String sessionCookieValue = session == null ? "" : session.getValue();
        Map<String,String> result = new LinkedHashMap<String,String>();
        StringTokenizer tokens = new StringTokenizer(sessionCookieValue, " ");
        if(tokens.countTokens() == 1) {
            result.put(DEFAULT_ALIAS, tokens.nextToken());
            return result;
        }
        while(tokens.hasMoreTokens()) {
            String alias = tokens.nextToken();
            String id = tokens.nextToken();
            result.put(alias, id);
        }
        return result;
    }

    @Override
    public HttpServletRequest wrapRequest(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute(HttpSessionManager.class.getName(), this);
        return request;
    }

    @Override
    public HttpServletResponse wrapResponse(HttpServletRequest request, HttpServletResponse response) {
        return new MultiSessionHttpServletResponse(response, request);
    }

    class MultiSessionHttpServletResponse extends HttpServletResponseWrapper {
        private final HttpServletRequest request;

        public MultiSessionHttpServletResponse(HttpServletResponse response, HttpServletRequest request) {
            super(response);
            this.request = request;
        }

        @Override
        public String encodeRedirectURL(String url) {
            url = super.encodeRedirectURL(url);
            return CookieHttpSessionStrategy.this.encodeURL(url, getCurrentSessionAlias(request));
        }

        @Override
        public String encodeURL(String url) {
            url = super.encodeURL(url);

            String alias = getCurrentSessionAlias(request);
            return CookieHttpSessionStrategy.this.encodeURL(url, alias);
        }
    }

    @Override
    public String encodeURL(String url, String sessionAlias) {
        int queryStart = url.indexOf("?");
        boolean isDefaultAlias = DEFAULT_ALIAS.equals(sessionAlias);
        if(queryStart < 0) {
            return isDefaultAlias ? url : url + "?" + sessionParam + "=" + sessionAlias;
        }
        String path = url.substring(0, queryStart);
        String query = url.substring(queryStart + 1, url.length());
        String replacement = isDefaultAlias ? "" : "$1"+sessionAlias;
        query = query.replaceFirst( "((^|&)" + sessionParam + "=)([^&]+)?", replacement);
        if(!isDefaultAlias && url.endsWith(query)) {
            // no existing alias
            if(!(query.endsWith("&") || query.length() == 0)) {
                query += "&";
            }
            query += sessionParam + "=" + sessionAlias;
        }

        return path + "?" + query;
    }
}