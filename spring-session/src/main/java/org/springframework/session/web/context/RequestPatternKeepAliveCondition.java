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
package org.springframework.session.web.context;

import javax.servlet.http.HttpServletRequest;

import org.springframework.session.keepalive.KeepAliveCondition;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UrlPathHelper;

/**
 * Thread local request path pattern matcher condition for integration with Spring framework.
 * All requests matching the defined exclude patterns will leave the session's last modified time unchanged.
 * (Useful for long-polling implementations)
 * @see <a href="http://en.wikipedia.org/wiki/Push_technology#Long_polling">Long polling</a>
 * Registration of {@link org.springframework.web.context.request.RequestContextListener} is required.
 * 
 * @see <a href="http://google.com">http://google.com</a>
 * @since 1.0.1
 * @author Peter Lajko
 */
public class RequestPatternKeepAliveCondition implements KeepAliveCondition {

	private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

	private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

	private String[] excludes;

	private static HttpServletRequest getThreadLocalRequest() {
		return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
	}

	private static boolean matchesAnyOf(String[] patterns) {
		if (patterns != null) {
			String lookupPath = PATH_HELPER.getPathWithinApplication(getThreadLocalRequest());
			for (String pattern : patterns) {
				if (PATH_MATCHER.match(pattern, lookupPath)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean keepAlive() {
		// keep the session alive if none of the excludes matched
		return !matchesAnyOf(excludes);
	}

	public String[] getExcludes() {
		return excludes;
	}

	public void setExcludes(String[] excludes) {
		this.excludes = excludes;
	}
}
