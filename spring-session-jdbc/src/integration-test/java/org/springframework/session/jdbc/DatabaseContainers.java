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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.testcontainers.containers.Db2Container;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

/**
 * Factories for various {@link JdbcDatabaseContainer}s.
 *
 * @author Vedran Pavic
 */
final class DatabaseContainers {

	private DatabaseContainers() {
	}

	static Db211Container db211() {
		return new Db211Container();
	}

	static MariaDBContainer mariaDb5() {
		return new MariaDb5Container();
	}

	static MariaDBContainer mariaDb10() {
		return new MariaDb10Container();
	}

	static MySQLContainer mySql5() {
		return new MySql5Container();
	}

	static MySQLContainer mySql8() {
		return new MySql8Container();
	}

	static OracleXeContainer oracleXe() {
		return new OracleXeContainer();
	}

	static PostgreSQLContainer postgreSql9() {
		return new PostgreSql9Container();
	}

	static PostgreSQLContainer postgreSql10() {
		return new PostgreSql10Container();
	}

	static PostgreSQLContainer postgreSql11() {
		return new PostgreSql11Container();
	}

	static MSSQLServerContainer sqlServer2017() {
		return new SqlServer2017Container();
	}

	private static class Db211Container extends Db2Container {

		Db211Container() {
			super("ibmcom/db2:11.5.0.0a");
		}

	}

	private static class MariaDb5Container extends MariaDBContainer<MariaDb5Container> {

		MariaDb5Container() {
			super("mariadb:5.5.64");
		}

		@Override
		protected void configure() {
			super.configure();
			setCommand("mysqld", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
					"--innodb_large_prefix", "--innodb_file_format=barracuda", "--innodb-file-per-table");
		}

	}

	private static class MariaDb10Container extends MariaDBContainer<MariaDb10Container> {

		MariaDb10Container() {
			super("mariadb:10.4.8");
		}

		@Override
		protected void configure() {
			super.configure();
			setCommand("mysqld", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
		}

	}

	private static class MySql5Container extends MySQLContainer<MySql5Container> {

		MySql5Container() {
			super("mysql:5.7.27");
		}

		@Override
		protected void configure() {
			super.configure();
			setCommand("mysqld", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
		}

		@Override
		public String getDriverClassName() {
			return "com.mysql.cj.jdbc.Driver";
		}

	}

	private static class MySql8Container extends MySQLContainer<MySql8Container> {

		MySql8Container() {
			super("mysql:8.0.17");
		}

		@Override
		protected void configure() {
			super.configure();
			setCommand("mysqld", "--default-authentication-plugin=mysql_native_password");
		}

		@Override
		public String getDriverClassName() {
			return "com.mysql.cj.jdbc.Driver";
		}

	}

	private static class OracleXeContainer extends OracleContainer {

		@Override
		protected void configure() {
			super.configure();
			this.waitStrategy = new LogMessageWaitStrategy().withRegEx(".*DATABASE IS READY TO USE!.*\\s")
					.withStartupTimeout(Duration.of(10, ChronoUnit.MINUTES));
			setShmSize(1024L * 1024L * 1024L);
			addEnv("ORACLE_PWD", getPassword());
		}

		@Override
		protected void waitUntilContainerStarted() {
			getWaitStrategy().waitUntilReady(this);
		}

	}

	private static class PostgreSql9Container extends PostgreSQLContainer<PostgreSql9Container> {

		PostgreSql9Container() {
			super("postgres:9.6.15");
		}

	}

	private static class PostgreSql10Container extends PostgreSQLContainer<PostgreSql10Container> {

		PostgreSql10Container() {
			super("postgres:10.10");
		}

	}

	private static class PostgreSql11Container extends PostgreSQLContainer<PostgreSql11Container> {

		PostgreSql11Container() {
			super("postgres:11.5");
		}

	}

	private static class SqlServer2017Container extends MSSQLServerContainer<SqlServer2017Container> {

		SqlServer2017Container() {
			super("mcr.microsoft.com/mssql/server:2017-CU16");
		}

	}

}
