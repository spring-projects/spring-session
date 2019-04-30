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

package org.springframework.session;

import java.util.Collections;
import java.util.Map;

import org.springframework.util.Assert;

/**
 * Base class for {@link IndexResolver}s that resolve a single index.
 *
 * @param <S> the type of Session being handled
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 2.2.0
 */
public abstract class SingleIndexResolver<S extends Session> implements IndexResolver<S> {

	private final String indexName;

	protected SingleIndexResolver(String indexName) {
		Assert.notNull(indexName, "Index name must not be null");
		this.indexName = indexName;
	}

	protected String getIndexName() {
		return this.indexName;
	}

	public abstract String resolveIndexValueFor(S session);

	public final Map<String, String> resolveIndexesFor(S session) {
		String indexValue = resolveIndexValueFor(session);
		return (indexValue != null) ? Collections.singletonMap(this.indexName, indexValue) : Collections.emptyMap();
	}

}
