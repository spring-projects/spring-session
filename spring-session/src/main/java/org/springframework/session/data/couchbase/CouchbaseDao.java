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
package org.springframework.session.data.couchbase;

import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.error.DocumentDoesNotExistException;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.N1qlQueryResult;

import org.springframework.data.couchbase.core.CouchbaseQueryExecutionException;
import org.springframework.data.couchbase.core.CouchbaseTemplate;

/**
 * Data access object that communicates with Couchbase database in order to manage HTTP
 * session data persistence.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
public class CouchbaseDao {

	protected final CouchbaseTemplate couchbase;

	public CouchbaseDao(CouchbaseTemplate couchbase) {
		this.couchbase = couchbase;
	}

	public void updatePutPrincipalSession(String principal, String sessionId) {
		String statement = "UPDATE default USE KEYS $1 SET sessionIds = ARRAY_PUT(sessionIds, $2)";
		executeQuery(statement, principal, sessionId);
	}

	public void updateRemovePrincipalSession(String principal, String sessionId) {
		String statement = "UPDATE default USE KEYS $1 SET sessionIds = ARRAY_REMOVE(sessionIds, $2)";
		executeQuery(statement, principal, sessionId);
	}

	public SessionDocument findById(String id) {
		return this.couchbase.findById(id, SessionDocument.class);
	}

	public PrincipalSessionsDocument findByPrincipal(String principal) {
		return this.couchbase.findById(principal, PrincipalSessionsDocument.class);
	}

	public void updateExpirationTime(String id, int expiry) {
		this.couchbase.getCouchbaseBucket().touch(id, expiry);
	}

	public void save(SessionDocument document) {
		this.couchbase.save(document);
	}

	public void save(PrincipalSessionsDocument document) {
		this.couchbase.save(document);
	}

	public boolean exists(String documentId) {
		return this.couchbase.exists(documentId);
	}

	public void delete(String id) {
		try {
			this.couchbase.remove(id);
		}
		catch (DocumentDoesNotExistException ex) {
			// Do nothing
		}
	}

	private N1qlQueryResult executeQuery(String statement, Object... parameters) {
		N1qlQueryResult result = this.couchbase.queryN1QL(
				N1qlQuery.parameterized(statement, JsonArray.from(parameters)));
		if (!result.finalSuccess()) {
			throw new CouchbaseQueryExecutionException("Error executing N1QL statement '"
					+ statement + "'. " + result.errors());
		}
		return result;
	}
}
