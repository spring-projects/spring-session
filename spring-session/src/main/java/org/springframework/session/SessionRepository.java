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

package org.springframework.session;

/**
 * A repository interface for managing {@link Session} instances.
 *
 * @param <S> the {@link Session} type
 * @author Rob Winch
 * @since 1.0
 */
public interface SessionRepository<S extends Session> {

	/**
	 * Creates a new {@link Session} that is capable of being persisted by this
	 * {@link SessionRepository}.
	 *
	 * <p>
	 * This allows optimizations and customizations in how the {@link Session} is
	 * persisted. For example, the implementation returned might keep track of the changes
	 * ensuring that only the delta needs to be persisted on a save.
	 * </p>
	 *
	 * @return a new {@link Session} that is capable of being persisted by this
	 * {@link SessionRepository}
	 */
	S createSession();

	/**
	 * Ensures the {@link Session} created by
	 * {@link org.springframework.session.SessionRepository#createSession()} is saved.
	 *
	 * <p>
	 * Some implementations may choose to save as the {@link Session} is updated by
	 * returning a {@link Session} that immediately persists any changes. In this case,
	 * this method may not actually do anything.
	 * </p>
	 *
	 * @param session the {@link Session} to save
	 */
	void save(S session);

	/**
	 * Gets the {@link Session} by the {@link Session#getId()} or null if no
	 * {@link Session} is found.
	 *
	 * @param id the {@link org.springframework.session.Session#getId()} to lookup
	 * @return the {@link Session} by the {@link Session#getId()} or null if no
	 * {@link Session} is found.
	 */
	S getSession(String id);

	/**
	 * Deletes the {@link Session} with the given {@link Session#getId()} or does nothing
	 * if the {@link Session} is not found.
	 * @param id the {@link org.springframework.session.Session#getId()} to delete
	 */
	void delete(String id);
}
