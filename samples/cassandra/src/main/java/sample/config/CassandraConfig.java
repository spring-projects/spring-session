package sample.config;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.exceptions.AlreadyExistsException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.session.data.cassandra.CassandraSessionRepository;

/**
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

	@Bean
	public com.datastax.driver.core.Session session() {
		com.datastax.driver.core.Session session = Cluster.builder()
				.addContactPoint("localhost")
				.withPort(9142)
				.build().connect();
		try {
			session.execute(CREATE_KEYSPACE);
		}
		catch (AlreadyExistsException e) {

		}
		session.execute("use spring_session");
		session.execute(CREATE_SESSION_TABLE);
		session.execute(STRING_CREATE_IDX_TABLE);
		return session;
	}

	@Bean
	public CassandraOperations cassandraOperations(com.datastax.driver.core.Session session) {
		return new CassandraTemplate(session);
	}

	@Bean
	public CassandraSessionRepository cassandraSessionRepository(CassandraOperations cassandraOperations) {
		return new CassandraSessionRepository(cassandraOperations);
	}
}
