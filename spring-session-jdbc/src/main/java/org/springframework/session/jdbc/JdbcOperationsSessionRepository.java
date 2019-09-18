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

package org.springframework.session.jdbc;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * This {@link SessionRepository} implementation is kept in order to support migration to
 * {@link JdbcIndexedSessionRepository} in a backwards compatible manner.
 *
 * @author Vedran Pavic
 * @author Craig Andrews
 * @since 1.2.0
 * @deprecated since 2.2.0 in favor of {@link JdbcIndexedSessionRepository}
 */
@Deprecated
public class JdbcOperationsSessionRepository extends JdbcIndexedSessionRepository {

	/**
	 * Create a new {@link JdbcOperationsSessionRepository} instance which uses the
	 * provided {@link JdbcOperations} and {@link TransactionOperations} to manage
	 * sessions.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 * @param transactionOperations the {@link TransactionOperations} to use
	 * @see JdbcIndexedSessionRepository#JdbcIndexedSessionRepository(JdbcOperations,
	 * TransactionOperations)
	 */
	public JdbcOperationsSessionRepository(JdbcOperations jdbcOperations, TransactionOperations transactionOperations) {
		super(jdbcOperations, transactionOperations);
	}

	/**
	 * Create a new {@link JdbcIndexedSessionRepository} instance which uses the provided
	 * {@link JdbcOperations} to manage sessions.
	 * <p>
	 * The created instance will execute all data access operations in a transaction with
	 * propagation level of {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 * @param transactionManager the {@link PlatformTransactionManager} to use
	 * @deprecated since 2.2.0 in favor of
	 * {@link JdbcIndexedSessionRepository#JdbcIndexedSessionRepository(JdbcOperations, TransactionOperations)}
	 */
	@Deprecated
	public JdbcOperationsSessionRepository(JdbcOperations jdbcOperations,
			PlatformTransactionManager transactionManager) {
		super(jdbcOperations, createTransactionTemplate(transactionManager));
	}

	/**
	 * Create a new {@link JdbcIndexedSessionRepository} instance which uses the provided
	 * {@link JdbcOperations} to manage sessions.
	 * <p>
	 * The created instance will not execute data access operations in a transaction.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 * @deprecated since 2.2.0 in favor of
	 * {@link JdbcIndexedSessionRepository#JdbcIndexedSessionRepository(JdbcOperations, TransactionOperations)}
	 */
	@Deprecated
	public JdbcOperationsSessionRepository(JdbcOperations jdbcOperations) {
		super(jdbcOperations, TransactionOperations.withoutTransaction());
	}

	private static TransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager) {
		Assert.notNull(transactionManager, "transactionManager must not be null");
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		transactionTemplate.afterPropertiesSet();
		return transactionTemplate;
	}

}
