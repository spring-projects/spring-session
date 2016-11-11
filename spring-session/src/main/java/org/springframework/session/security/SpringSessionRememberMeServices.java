/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.util.Assert;

/**
 * A {@link RememberMeServices} implementation that uses Spring Session backed
 * {@link HttpSession} to provide remember-me service capabilities.
 *
 * @author Vedran Pavic
 * @since 1.3.0
 */
public class SpringSessionRememberMeServices implements RememberMeServices {

	/**
	 * Remember-me login request attribute name.
	 */
	public static final String REMEMBER_ME_LOGIN_ATTR = SpringSessionRememberMeServices.class
			.getName() + "REMEMBER_ME_LOGIN_ATTR";

	private static final String DEFAULT_PARAMETER = "remember-me";

	private static final int THIRTY_DAYS_SECONDS = 2592000;

	private static final Log logger = LogFactory.getLog(SpringSessionRememberMeServices.class);

	private String parameter = DEFAULT_PARAMETER;

	private boolean alwaysRemember;

	private int validitySeconds = THIRTY_DAYS_SECONDS;

	public final Authentication autoLogin(HttpServletRequest request,
			HttpServletResponse response) {
		return null;
	}

	public final void loginFail(HttpServletRequest request, HttpServletResponse response) {
		logger.debug("Interactive login attempt was unsuccessful.");
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

	public final void loginSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication successfulAuthentication) {
		if (!this.alwaysRemember && !rememberMeRequested(request, this.parameter)) {
			logger.debug("Remember-me login not requested.");
			return;
		}
		request.setAttribute(REMEMBER_ME_LOGIN_ATTR, true);
		request.getSession().setMaxInactiveInterval(this.validitySeconds);
	}

	/**
	 * Allows customization of whether a remember-me login has been requested. The default
	 * is to return {@code true} if the configured parameter name has been included in the
	 * request and is set to the value {@code true}.
	 * @param request the request submitted from an interactive login, which may include
	 * additional information indicating that a persistent login is desired.
	 * @param parameter the configured remember-me parameter name.
	 * @return true if the request includes information indicating that a persistent login
	 * has been requested.
	 */
	protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
		String rememberMe = request.getParameter(parameter);
		if (rememberMe != null) {
			if (rememberMe.equalsIgnoreCase("true") || rememberMe.equalsIgnoreCase("on")
					|| rememberMe.equalsIgnoreCase("yes") || rememberMe.equals("1")) {
				return true;
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Did not send remember-me cookie (principal did not set " +
					"parameter '" + parameter + "')");
		}
		return false;
	}

	/**
	 * Set the name of the parameter which should be checked for to see if a remember-me
	 * has been requested during a login request. This should be the same name you assign
	 * to the checkbox in your login form.
	 * @param parameter the request parameter
	 */
	public void setParameter(String parameter) {
		Assert.hasText(parameter, "Parameter name cannot be empty or null");
		this.parameter = parameter;
	}

	public void setAlwaysRemember(boolean alwaysRemember) {
		this.alwaysRemember = alwaysRemember;
	}

	public void setValiditySeconds(int validitySeconds) {
		this.validitySeconds = validitySeconds;
	}

}
