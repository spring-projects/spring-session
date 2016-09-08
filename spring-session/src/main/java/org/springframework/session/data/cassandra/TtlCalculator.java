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


import org.springframework.session.ExpiringSession;

/**
 * Calculate the time to live in seconds for a given {@link ExpiringSession}.
 * @author Andrew Fitzgerald
 */
public class TtlCalculator {

	public int calculateTtlInSeconds(long currentTime, ExpiringSession session) {
		long millisSinceAccess = currentTime - session.getLastAccessedTime();
		long secondsSinceAccess = millisSinceAccess / 1000;
		long secondsToLive = session.getMaxInactiveIntervalInSeconds() - secondsSinceAccess;
		if (secondsToLive <= 0) {
			throw new IllegalArgumentException("Session has already expired");
		}
		//cast is safe since seconds to live is always less than maxInactiveIntervalInSeconds, which is an int.
		return (int) secondsToLive;
	}

}
