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

package org.springframework.session.data.cassandra.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Add this annotation to a {@code @Configuration} class to expose the
 * SessionRepositoryFilter as a bean named "springSessionRepositoryFilter" and backed by
 * Cassandra. The annotation properties can be used to control the way the cluster is used
 * to store sessions.
 *
 * @author Andrew Fitzgerald
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(CassandraHttpSessionConfiguration.class)
@Configuration
public @interface EnableCassandraHttpSession {

	/**
	 * The name of cassandra table used by Spring Session to store sessions.
	 * @return the cassandra table name
	 */
	String tableName() default "spring_session";

	/**
	 * The name of cassandra keyspace to connect to.
	 * @return the cassandra keyspace
	 */
	String keyspace() default "";

	/**
	 * The list of cassandra contact points.
	 * @return the cassandra contact points
	 */
	String[] contactPoints() default "localhost";

	/**
	 * The cassandra port to connect to.
	 * @return the cassandra port
	 */
	int port() default 9042;

	/**
	 * The session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
	 * This should be a non-negative integer.
	 *
	 * @return the seconds a session can be inactive before expiring
	 */
	int maxInactiveIntervalInSeconds() default 1800;

}
