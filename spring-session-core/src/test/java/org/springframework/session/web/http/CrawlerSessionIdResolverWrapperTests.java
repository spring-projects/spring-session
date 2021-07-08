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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import static org.assertj.core.api.Assertions.assertThat;


public class CrawlerSessionIdResolverWrapperTests {
	public static final String DEFAULT_CRAWLER_USER_AGENT = "bot.Feedfetcher-Google.com";
	public static final String NON_CRAWLER_USER_AGENT = "some.user.agent";
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private CrawlerSessionIdResolverWrapper strategy;
	private String ipOne = "1.1.1.1";
	private String ipTwo = "1.1.1.2";

	@BeforeEach
	public void setup() throws Exception {
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.strategy = new CrawlerSessionIdResolverWrapper(new CookieHttpSessionIdResolver());
	}

	@Test
	public void getRequestedSessionIdNullDefaultCrawler() {
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", DEFAULT_CRAWLER_USER_AGENT);
		assertThat(this.strategy.resolveSessionIds(this.request).isEmpty());
	}

	@Test
	public void getRequestedSessionIdNullNotCrawler() {
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", NON_CRAWLER_USER_AGENT);
		assertThat(this.strategy.resolveSessionIds(this.request).isEmpty());
	}

	@Test
	public void onNewSessionFromNonCrawler() {
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", NON_CRAWLER_USER_AGENT);
		Session session = new MapSession();
		this.strategy.setSessionId(this.request, this.response, session.getId());
		assertThat(this.strategy.getSessionIdForIp(this.ipOne)).isNull();
	}

	@Test
	public void onNewSessionFromDefaultCrawler() {
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", DEFAULT_CRAWLER_USER_AGENT);
		Session session = new MapSession();
		this.strategy.setSessionId(this.request, this.response, session.getId());
		assertThat(this.strategy.getSessionIdForIp(this.ipOne)).isEqualTo(session.getId());
	}

	@Test
	public void onNewSessionFromCustomCrawler() {
		this.strategy.setCrawlerUserAgents(".*some-bot.*");
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", "crawl.some-bot.we");
		Session session = new MapSession();
		this.strategy.setSessionId(this.request, this.response, session.getId());
		assertThat(this.strategy.getSessionIdForIp(this.ipOne)).isEqualTo(session.getId());
	}

	@Test
	public void onMultipleSessionFromSameCrawler() {
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", DEFAULT_CRAWLER_USER_AGENT);
		Session sessionOne = new MapSession();
		this.strategy.setSessionId(this.request, this.response, sessionOne.getId());

		this.request.setRemoteAddr(this.ipTwo);
		Session sessionTwo = new MapSession();
		this.strategy.setSessionId(this.request, this.response, sessionTwo.getId());

		assertThat(this.strategy.getSessionIdForIp(this.ipOne)).isEqualTo(sessionOne.getId());
		assertThat(this.strategy.getSessionIdForIp(this.ipTwo)).isEqualTo(sessionTwo.getId());
	}

	@Test
	public void onInvalidateSessionCachedCrawler() {
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", DEFAULT_CRAWLER_USER_AGENT);
		Session session = new MapSession();
		this.strategy.setSessionId(this.request, this.response, session.getId());
		this.strategy.expireSession(this.request, this.response);
		assertThat(this.strategy.getSessionIdForIp(this.ipOne)).isNull();
	}

	@Test
	public void onInvalidateSessionNonCrawler() {
		this.request.setRemoteAddr(this.ipOne);
		this.request.addHeader("user-agent", NON_CRAWLER_USER_AGENT);
		Session session = new MapSession();
		this.strategy.setSessionId(this.request, this.response, session.getId());
		this.strategy.expireSession(this.request, this.response);
		assertThat(this.strategy.getSessionIdForIp(this.ipOne)).isNull();
	}
}
