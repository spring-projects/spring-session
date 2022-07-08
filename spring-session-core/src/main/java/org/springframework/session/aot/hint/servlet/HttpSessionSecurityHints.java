/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.session.aot.hint.servlet;

import java.util.Arrays;
import java.util.Locale;
import java.util.TreeMap;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.SavedCookie;
import org.springframework.util.ClassUtils;

/**
 * {@link RuntimeHintsRegistrar} for Servlet Session hints.
 *
 * @author Marcus Da Coregio
 */
public class HttpSessionSecurityHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		if (!ClassUtils.isPresent("jakarta.servlet.http.HttpSession", classLoader)
				|| !ClassUtils.isPresent("org.springframework.security.web.csrf.DefaultCsrfToken", classLoader)) {
			return;
		}
		Arrays.asList(TypeReference.of(TreeMap.class), TypeReference.of(Locale.class),
				TypeReference.of(DefaultSavedRequest.class), TypeReference.of(DefaultCsrfToken.class),
				TypeReference.of(WebAuthenticationDetails.class), TypeReference.of(SavedCookie.class),
				TypeReference.of("java.lang.String$CaseInsensitiveComparator"))
				.forEach(hints.serialization()::registerType);
	}

}
