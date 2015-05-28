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
package org.springframework.session.keepalive;

/**
 * Interface for defining a conditional session keep-alive.
 * 
 * @since 1.0.1
 * @author Peter Lajko
 */
public interface KeepAliveCondition {

	/** Always return true (Recommended default) */
	KeepAliveCondition ALWAYS = new KeepAliveCondition() {
		@Override
		public boolean keepAlive() {
			return true;
		}
	};

	/**
	 * Whether to keep alive the actual session by modifying the last access time.
	 *
	 * @return true, if last access time must be refreshed
	 */
	boolean keepAlive();
}