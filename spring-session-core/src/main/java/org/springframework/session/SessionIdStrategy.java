/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.session;

/**
 * Provides a common interface for id generation strategy. Allows customization of session
 * id creation process.
 *
 * @author Jakub Maciej
 */
public interface SessionIdStrategy {

	/**
	 * Gets a unique string that identifies the {@link Session}.
	 * @return a unique string that identifies the {@link Session}
	 */
	String createSessionId();

	/**
	 * Gets default strategy used to generate session id {@link SessionIdStrategy}.
	 * @return gets default strategy used to generate session id {@link SessionIdStrategy}
	 */
	static SessionIdStrategy getDefaultGenerationStrategy() {
		return new UUIDSessionIdStrategy();
	}

}
