/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session.id;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

public class SecureRandomSessionIdStrategyTests {
	
	private SecureRandomSessionIdStrategy strategy;

	@Before
	public void setup() {
		strategy = new SecureRandomSessionIdStrategy();
		strategy.setByteLength(20);
		strategy.setEncoder(new HexSessionIdEncoder(true));
	}

	@Test
	public void createSessionId() {
		String id = strategy.createSessionId();
		assertThat(id.length()).isEqualTo(40);
		assertThat(id).isEqualTo(id.toLowerCase(Locale.US));
	}
	

}