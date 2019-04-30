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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link IndexResolver} that resolves indexes using multiple @{link IndexResolver}
 * delegates.
 *
 * @param <S> the type of Session being handled
 * @author Vedran Pavic
 * @since 2.2.0
 */
public class DelegatingIndexResolver<S extends Session> implements IndexResolver<S> {

	private final List<IndexResolver<S>> delegates;

	public DelegatingIndexResolver(List<IndexResolver<S>> delegates) {
		this.delegates = Collections.unmodifiableList(delegates);
	}

	@SafeVarargs
	public DelegatingIndexResolver(IndexResolver<S>... delegates) {
		this(Arrays.asList(delegates));
	}

	public Map<String, String> resolveIndexesFor(S session) {
		Map<String, String> indexes = new HashMap<>();
		for (IndexResolver<S> delegate : this.delegates) {
			indexes.putAll(delegate.resolveIndexesFor(session));
		}
		return indexes;
	}

}
