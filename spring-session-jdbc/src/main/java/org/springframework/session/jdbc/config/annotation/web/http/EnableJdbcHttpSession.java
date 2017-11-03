/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.jdbc.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.sql.DataSource;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.MapSession;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * {@link SessionRepositoryFilter} as a bean named {@code springSessionRepositoryFilter}
 * and backed by a relational database. In order to leverage the annotation, a single
 * {@link DataSource} must be provided. For example:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableJdbcHttpSession
 * public class JdbcHttpSessionConfig {
 *
 *     &#064;Bean
 *     public DataSource dataSource() {
 *         return new EmbeddedDatabaseBuilder()
 *                 .setType(EmbeddedDatabaseType.H2)
 *                 .addScript("org/springframework/session/jdbc/schema-h2.sql")
 *                 .build();
 *     }
 *
 *     &#064;Bean
 *     public PlatformTransactionManager transactionManager(DataSource dataSource) {
 *         return new DataSourceTransactionManager(dataSource);
 *     }
 *
 * }
 * </pre>
 *
 * More advanced configurations can extend {@link JdbcHttpSessionConfiguration} instead.
 *
 * For additional information on how to configure data access related concerns, please
 * refer to the <a href=
 * "http://docs.spring.io/spring/docs/current/spring-framework-reference/html/spring-data-tier.html">
 * Spring Framework Reference Documentation</a>.
 *
 * @author Vedran Pavic
 * @since 1.2.0
 * @see EnableSpringHttpSession
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(JdbcHttpSessionConfiguration.class)
@Configuration
public @interface EnableJdbcHttpSession {

	/**
	 * The session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
	 * This should be a non-negative integer.
	 * @return the seconds a session can be inactive before expiring
	 */
	int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * The name of database table used by Spring Session to store sessions.
	 * @return the database table name
	 */
	String tableName() default JdbcOperationsSessionRepository.DEFAULT_TABLE_NAME;

	/**
	 * The cron expression for expired session cleanup job. By default runs every minute.
	 * @return the session cleanup cron expression
	 * @since 2.0.0
	 */
	String cleanupCron() default JdbcHttpSessionConfiguration.DEFAULT_CLEANUP_CRON;

}
