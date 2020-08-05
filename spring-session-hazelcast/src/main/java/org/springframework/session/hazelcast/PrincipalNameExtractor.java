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

package org.springframework.session.hazelcast;

import com.hazelcast.query.extractor.ValueCollector;
import com.hazelcast.query.extractor.ValueExtractor;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

/**
 * Hazelcast {@link ValueExtractor} responsible for extracting principal name from the
 * {@link MapSession}.
 *
 * @author Vedran Pavic
 * @since 1.3.0
 */
public class PrincipalNameExtractor implements ValueExtractor<MapSession, String> {

	@Override
	@SuppressWarnings("unchecked")
	public void extract(MapSession target, String argument, ValueCollector collector) {
		String principalName = target.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		if (principalName != null) {
			collector.addObject(principalName);
		}
	}

}
