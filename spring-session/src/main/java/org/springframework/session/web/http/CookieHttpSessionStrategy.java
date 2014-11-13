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

import org.springframework.session.Session;
import org.springframework.util.Assert;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A {@link HttpSessionStrategy} that uses a cookie to obtain the session from. Specifically, this implementation will
 * allow specifying a cookie name using {@link CookieHttpSessionStrategy#setCookieName(String)}. The default is "SESSION".
 *
 * When a session is created, the HTTP response will have a cookie with the specified cookie name and the value of the
 * session id. The cookie will be marked as a session cookie, use the context path for the path of the cookie, marked as
 * HTTPOnly, and if {@link javax.servlet.http.HttpServletRequest#isSecure()} returns true, the cookie will be marked as
 * secure. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Path=/context-root; Secure; HttpOnly
 * </pre>
 *
 * The client should now include the session in each request by specifying the same cookie in their request. For example:
 *
 * <pre>
 * GET /messages/ HTTP/1.1
 * Host: example.com
 * Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 * </pre>
 *
 * When the session is invalidated, the server will send an HTTP response that expires the cookie. For example:
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Set-Cookie: SESSION=f81d4fae-7dec-11d0-a765-00a0c91e6bf6; Expires=Thur, 1 Jan 1970 00:00:00 GMT; Secure; HttpOnly
 * </pre>
 *
 * @since 1.0
 * @author Rob Winch
 */
public final class CookieHttpSessionStrategy implements HttpSessionStrategy {
    private String cookieName = "SESSION";

    @Override
    public String getRequestedSessionId(HttpServletRequest request) {
        Cookie session = getCookie(request, cookieName);
        return session == null ? null : session.getValue();
    }

    @Override
    public void onNewSession(Session session, HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, session.getId());
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(cookiePath(request));
        response.addCookie(cookie);

        // TODO set the domain?
    }

    @Override
    public void onInvalidateSession(HttpServletRequest request, HttpServletResponse response) {
        Cookie sessionCookie = new Cookie(cookieName,"");
        sessionCookie.setMaxAge(0);
        sessionCookie.setHttpOnly(true);
        sessionCookie.setSecure(request.isSecure());
        sessionCookie.setPath(cookiePath(request));
        response.addCookie(sessionCookie);
    }

    /**
     * Sets the name of the cookie to be used
     * @param cookieName
     */
    public void setCookieName(String cookieName) {
        Assert.notNull(cookieName, "cookieName cannot be null");
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
        Assert.notNull(request, "Request must not be null");
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
}