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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;

/**
 * A {@link FindByIndexNameSessionRepository} that decorates the delegating
 * {@link SessionRepository}, ignoring the configured set of exceptions during retrieval
 * of a {@link Session} from the underlying store. Useful with handling exceptions
 * during session deserialization (for example, when serialization UID changes) in
 * scenarios where {@link SessionRepository} consumer wants to treat the session that
 * wasn't deserializable as non-existing.
 * <p>
 * By default, this implementation will delete the session whose retrieval using
 * {@link #getSession(String)} has failed with the configured ignored exception. This
 * behavior can be changed using {@link #setDeleteOnIgnoredException(boolean)} method.
 *
 * @param <S> the type of {@link Session} being managed
 * @author Vedran Pavic
 * @since 1.3.0
 */
public class SafeRetrievingSessionRepository<S extends Session>
		implements FindByIndexNameSessionRepository<S> {

	private static final Log logger = LogFactory.getLog(SafeRetrievingSessionRepository.class);

	private final FindByIndexNameSessionRepository<S> delegate;

	private final Set<Class<? extends RuntimeException>> ignoredExceptions;

	private boolean deleteOnIgnoredException = true;

	private boolean logIgnoredException;

	/**
	 * Create a new {@link SafeRetrievingSessionRepository} instance backed by a
	 * {@link FindByIndexNameSessionRepository} delegate.
	 * @param delegate the {@link FindByIndexNameSessionRepository} delegate
	 * @param ignoredExceptions the set of exceptions to ignore
	 */
	public SafeRetrievingSessionRepository(
			FindByIndexNameSessionRepository<S> delegate,
			Set<Class<? extends RuntimeException>> ignoredExceptions) {
		Assert.notNull(delegate, "Delegate must not be null");
		Assert.notEmpty(ignoredExceptions, "Ignored exceptions must not be empty");
		this.delegate = delegate;
		this.ignoredExceptions = ignoredExceptions;
	}

	/**
	 * Create a new {@link SafeRetrievingSessionRepository} instance backed by a
	 * {@link SessionRepository} delegate.
	 * @param delegate the {@link SessionRepository} delegate
	 * @param ignoredExceptions the set of exceptions to ignore
	 */
	public SafeRetrievingSessionRepository(SessionRepository<S> delegate,
			Set<Class<? extends RuntimeException>> ignoredExceptions) {
		this(new FindByIndexNameSessionRepositoryAdapter<S>(delegate), ignoredExceptions);
	}

	/**
	 * Set whether session should be deleted after ignored exception has occurred during
	 * retrieval.
	 * @param deleteOnIgnoredException the flag to indicate whether session should be
	 * deleted
	 */
	public void setDeleteOnIgnoredException(boolean deleteOnIgnoredException) {
		this.deleteOnIgnoredException = deleteOnIgnoredException;
	}

	/**
	 * Set whether ignored exception should be logged.
	 * @param logIgnoredException the flag to indicate whether to log ignored exceptions
	 */
	public void setLogIgnoredException(boolean logIgnoredException) {
		this.logIgnoredException = logIgnoredException;
	}

	public S createSession() {
		return this.delegate.createSession();
	}

	public void save(S session) {
		this.delegate.save(session);
	}

	public S getSession(String id) {
		try {
			return this.delegate.getSession(id);
		}
		catch (RuntimeException e) {
			if (isIgnoredException(e)) {
				if (this.logIgnoredException) {
					logger.warn("Error occurred while retrieving session " + id + ": "
							+ e);
				}
				if (this.deleteOnIgnoredException) {
					this.delegate.delete(id);
				}
				return null;
			}
			throw e;
		}
	}

	public void delete(String id) {
		this.delegate.delete(id);
	}

	public Map<String, S> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		try {
			return this.delegate.findByIndexNameAndIndexValue(indexName, indexValue);
		}
		catch (RuntimeException e) {
			if (this.logIgnoredException) {
				logger.warn("Error occurred while retrieving sessions with index name '"
						+ indexName + "' and value '" + indexValue + "': " + e);
			}
			if (isIgnoredException(e)) {
				return Collections.emptyMap();
			}
			throw e;
		}
	}

	public boolean isIgnoredException(RuntimeException e) {
		return this.ignoredExceptions.contains(e.getClass());
	}

	private static class FindByIndexNameSessionRepositoryAdapter<S extends Session>
			implements FindByIndexNameSessionRepository<S> {

		private final SessionRepository<S> delegate;

		FindByIndexNameSessionRepositoryAdapter(SessionRepository<S> delegate) {
			Assert.notNull(delegate, "Delegate must not be null");
			this.delegate = delegate;
		}

		public S createSession() {
			return this.delegate.createSession();
		}

		public void save(S session) {
			this.delegate.save(session);
		}

		public S getSession(String id) {
			return this.delegate.getSession(id);
		}

		public void delete(String id) {
			this.delegate.delete(id);
		}

		public Map<String, S> findByIndexNameAndIndexValue(String indexName,
				String indexValue) {
			throw new UnsupportedOperationException();
		}

	}

}
