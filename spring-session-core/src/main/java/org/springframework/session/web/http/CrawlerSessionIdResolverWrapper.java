/*
 * Copyright 2014-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.web.http;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This wrapper {@link HttpSessionIdResolver} ensures that the crawlers are associated with just one session per host
 * regardless of whether or not the crawler provides session id. This class allows to configure pattern for matching
 * crawlers based on HTTP header user-agent using {@link CrawlerSessionIdResolverWrapper#setCrawlerUserAgents(String)}.
 * The default is to match ".*[bB]ot.*|.*Yahoo! Slurp.*|.*Feedfetcher-Google.*".
 * Please note that in the case of clustered environment, there will be a session for each host in the cluster.
 *
 * @author Thilak Thangapandian
 */
public class CrawlerSessionIdResolverWrapper implements HttpSessionIdResolver {

	private static final Log log = LogFactory.getLog(CrawlerSessionIdResolverWrapper.class);
	private final HttpSessionIdResolver delegate;
	private final Map<String, String> clientIpSessionId = new ConcurrentHashMap<>();
	private final Map<String, String> sessionIdClientIp = new ConcurrentHashMap<>();
	private String crawlerUserAgents = ".*[bB]ot.*|.*Yahoo! Slurp.*|.*Feedfeztcher-Google.*";
	private Pattern uaPattern = null;

	public CrawlerSessionIdResolverWrapper(HttpSessionIdResolver delegate) {
		this.delegate = delegate;
	}

	@Override
	public List<String> resolveSessionIds(HttpServletRequest request) {
		String sessionId = getSessionIdForCrawler(request);
		if (sessionId != null) {
			if (log.isDebugEnabled()) {
				log.debug(request.hashCode() + ": Resolved bot session. SessionID=" + sessionId);
			}
			return Collections.singletonList(sessionId);
		}
		else {
			return this.delegate.resolveSessionIds(request);
		}
	}

	@Override
	public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
		this.delegate.setSessionId(request, response, sessionId);
		if (sessionId != null && isKnownCrawler(request)) {
			String clientIp = request.getRemoteAddr();
			this.clientIpSessionId.put(clientIp, sessionId);
			this.sessionIdClientIp.put(sessionId, clientIp);
			if (log.isDebugEnabled()) {
				log.debug(request.hashCode() + ": New bot session. SessionID=" + sessionId);
			}
		}
	}

	@Override
	public void expireSession(HttpServletRequest request, HttpServletResponse response) {
		this.delegate.expireSession(request, response);
		if (isKnownCrawler(request)) {
			String clientIp = request.getRemoteAddr();
			String sessionId = this.clientIpSessionId.get(clientIp);
			if (sessionId != null) {
				removeIpSessionIdMapping(sessionId);
			}
		}
	}

	/**
	 * Specify the regular expression (using {@link Pattern}) that will be used
	 * to identify crawlers based in the User-Agent header provided. The default
	 * is ".*GoogleBot.*|.*bingbot.*|.*Yahoo! Slurp.*"
	 *
	 * @param crawlerUserAgents the regular expression using {@link Pattern}
	 */
	public void setCrawlerUserAgents(String crawlerUserAgents) {
		this.crawlerUserAgents = crawlerUserAgents;
		if (crawlerUserAgents == null || crawlerUserAgents.length() == 0) {
			this.uaPattern = null;
		}
		else {
			this.uaPattern = Pattern.compile(crawlerUserAgents);
		}
	}

	/**
	 * Get current regex to match crawler.
	 *
	 * @return the current regular expression being used to match user agents.
	 * @see #setCrawlerUserAgents(String)
	 */
	public String getCrawlerUserAgents() {
		return this.crawlerUserAgents;
	}

	public String getSessionIdForIp(String ip) {
		return this.clientIpSessionId.get(ip);
	}

	public String getClientIpForSessionId(String sessionId) {
		return this.sessionIdClientIp.get(sessionId);
	}

	private String getSessionIdForCrawler(HttpServletRequest request) {
		String sessionId = null;
		String clientIp = null;
		// Is this a known crawler
		if (isKnownCrawler(request)) {
			clientIp = request.getRemoteAddr();
			sessionId = this.clientIpSessionId.get(clientIp);
		}
		return sessionId;
	}

	private boolean isKnownCrawler(HttpServletRequest request) {
		boolean isBot = false;
		if (this.uaPattern == null) {
			this.uaPattern = Pattern.compile(this.crawlerUserAgents);
		}
		// Is this a crawler - check the UA headers
		Enumeration<String> uaHeaders = request.getHeaders("user-agent");
		String uaHeader = null;
		if (uaHeaders.hasMoreElements()) {
			uaHeader = uaHeaders.nextElement();
		}
		// If more than one UA header - assume not a bot
		if (uaHeader != null && !uaHeaders.hasMoreElements()) {
			if (log.isDebugEnabled()) {
				log.debug(request.hashCode() + ": UserAgent=" + uaHeader);
			}
			if (this.uaPattern.matcher(uaHeader).matches()) {
				isBot = true;
				if (log.isDebugEnabled()) {
					log.debug(request.hashCode() + ": Bot found. UserAgent=" + uaHeader);
				}
			}
		}
		return isBot;
	}

	private void removeIpSessionIdMapping(String sessionId) {
		String clientIp = this.sessionIdClientIp.remove(sessionId);
		if (clientIp != null) {
			this.clientIpSessionId.remove(clientIp);
		}
	}
}
