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
package sample.config;

import javax.annotation.PreDestroy;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.AlreadyExistsException;

import org.cassandraunit.utils.EmbeddedCassandraServerHelper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Example Cassandra initialization code. In a very similar way as the integration test suite,
 * {@code cassandra-unit} is leveraged to bring up a test cluster to connect to. Here you would
 * normally inject your Cassandra cluster connection information, as well as perform any kind of
 * schema pre-checks and initialization.
 *
 * @author Jesus Zazueta.
 */
@Configuration
public class CassandraConfig {

	static final String CREATE_KEYSPACE = "CREATE KEYSPACE spring_session WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};";
	static final String CREATE_SESSION_TABLE = "CREATE TABLE IF NOT EXISTS spring_session (\n" +
			"    id uuid PRIMARY KEY,\n" +
			"    attributes map<text, text>,\n" +
			"    creation_time bigint,\n" +
			"    last_accessed bigint,\n" +
			"    max_inactive_interval_in_seconds int\n" +
			");";
	static final String STRING_CREATE_IDX_TABLE = "CREATE TABLE IF NOT EXISTS spring_session_by_name (\n" +
			"    principal_name text,\n" +
			"    id uuid,\n" +
			"    PRIMARY KEY (principal_name, id)\n" +
			");";
	private Session session = null;

	@Bean
	public boolean initCluster() throws Exception {
		EmbeddedCassandraServerHelper.startEmbeddedCassandra();
		return true;
	}

	@Bean
	public com.datastax.driver.core.Session session() throws Exception {
		if (initCluster()) {
			this.session = Cluster.builder().addContactPoint("localhost").withPort(9142)
					.build().connect();
			try {
				this.session.execute(CREATE_KEYSPACE);
			}
			catch (AlreadyExistsException e) {

			}
			this.session.execute("use spring_session");
			this.session.execute(CREATE_SESSION_TABLE);
			this.session.execute(STRING_CREATE_IDX_TABLE);
		}
		return this.session;
	}

	@PreDestroy
	public void shutdown() throws Exception {
		if (this.session != null) {
			this.session.close();
		}
		EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
	}
}
