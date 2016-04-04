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
package org.springframework.session.data.couchbase.application;

import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;

/**
 * Integration tests Couchbase configuration.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
@Configuration
@EnableCouchbaseRepositories
public class CouchbaseConfiguration extends AbstractCouchbaseConfiguration {

	/**
	 * Couchbase bucket name.
	 */
	public static final String COUCHBASE_BUCKET_NAME = "default";

	@Override
	protected List<String> getBootstrapHosts() {
		return Collections.singletonList("localhost");
	}

	@Override
	protected String getBucketName() {
		return COUCHBASE_BUCKET_NAME;
	}

	@Override
	protected String getBucketPassword() {
		return "";
	}
}
