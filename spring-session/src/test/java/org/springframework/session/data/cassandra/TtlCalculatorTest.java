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

package org.springframework.session.data.cassandra;

import org.junit.Before;
import org.junit.Test;

import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;


public class TtlCalculatorTest {


	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 100;
	private final TtlCalculator calculator = new TtlCalculator();
	private ExpiringSession session;

	@Before
	public void setUp() throws Exception {
		this.session = new MapSession();
		this.session.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void itReturnsTheDefaultWhenLastAccessedIsNow() throws Exception {
		long lastAccessedTime = System.currentTimeMillis();
		long currentTime = lastAccessedTime;
		this.session.setLastAccessedTime(lastAccessedTime);

		int ttl = this.calculator.calculateTtlInSeconds(currentTime, this.session);

		assertThat(ttl).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}


	@Test
	public void itRoundsUp() throws Exception {
		long lastAccessedTime = System.currentTimeMillis();
		long currentTime = lastAccessedTime + 999;
		this.session.setLastAccessedTime(lastAccessedTime);

		int ttl = this.calculator.calculateTtlInSeconds(currentTime, this.session);

		assertThat(ttl).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void itSubtractsFromMaxInactiveInterval() throws Exception {
		long lastAccessedTime = System.currentTimeMillis();
		long currentTime = lastAccessedTime + 1000;
		this.session.setLastAccessedTime(lastAccessedTime);

		int ttl = this.calculator.calculateTtlInSeconds(currentTime, this.session);


		assertThat(ttl).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS - 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void itThrowsExpiredExceptionWhenAlreadyExpired() {
		long lastAccessedTime = System.currentTimeMillis();
		long currentTime = lastAccessedTime + MAX_INACTIVE_INTERVAL_IN_SECONDS * 1000;
		this.session.setLastAccessedTime(lastAccessedTime);

		this.calculator.calculateTtlInSeconds(currentTime, this.session);
	}
}
