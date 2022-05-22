/*
 * Copyright 2014-2022 the original author or authors.
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

import java.util.Map;

/**
 * Strategy interface for resolving the {@link Session}'s indexes.
 *
 * @param <S> the type of Session being handled
 * @author Rob Winch
 * @author Vedran Pavic
 * @author Andrei Stefan
 * @since 2.2.0
 * @see FindByIndexNameSessionRepository
 */
@FunctionalInterface
public interface IndexResolver<S extends Session> {

	/**
	 * Resolve indexes for the session.
	 * @param session the session
	 * @return a map of resolved indexes, never {@code null}
	 */
	Map<String, String> resolveIndexesFor(S session);

}
