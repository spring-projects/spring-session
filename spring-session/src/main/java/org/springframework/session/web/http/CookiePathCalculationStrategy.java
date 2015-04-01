package org.springframework.session.web.http;

import javax.servlet.http.HttpServletRequest;

/**
 * Interface which needs to be implemented to override the way
 * the Cookie Path in {@link CookieHttpSessionStrategy} is calculated
 */
public interface CookiePathCalculationStrategy {

    /**
     * Implement this method to override the path that should be set
     * for the Session-Cookie.
     *
     * <p>Make sure that the path always ends with a &quot;/&quot;</p>
     * @param request the {@link HttpServletRequest} which is used to calculate the path
     * @return a non-empty String ending with a &quot;/&quot;
     */
    String calculateCookiePath(HttpServletRequest request);
}
