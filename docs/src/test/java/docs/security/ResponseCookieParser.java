/*
 * Copyright 2014-2018 the original author or authors.
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

package docs.security;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.NonNull;

final class ResponseCookieParser {

	private ResponseCookieParser() {
	}

	static List<ResponseCookie> parse(HttpServletResponse response) {
		return doParse(response, null);
	}

	static ResponseCookie parse(HttpServletResponse response, String cookieName) {
		List<ResponseCookie> responseCookies = doParse(response, cookieName);
		return (!responseCookies.isEmpty() ? responseCookies.get(0) : null);
	}

	@NonNull
	private static List<ResponseCookie> doParse(HttpServletResponse response,
			String cookieName) {
		List<ResponseCookie> responseCookies = new ArrayList<>();
		for (String setCookieHeader : response.getHeaders(HttpHeaders.SET_COOKIE)) {
			String[] cookieParts = setCookieHeader.split("\\s*=\\s*", 2);
			if (cookieParts.length != 2) {
				return null;
			}
			String name = cookieParts[0];
			if (cookieName != null && !name.equals(cookieName)) {
				continue;
			}
			String[] valueAndDirectives = cookieParts[1].split("\\s*;\\s*", 2);
			String value = valueAndDirectives[0];
			String[] directives = valueAndDirectives[1].split("\\s*;\\s*");
			String domain = null;
			int maxAge = -1;
			String path = null;
			boolean secure = false;
			boolean httpOnly = false;
			String sameSite = null;
			for (String directive : directives) {
				if (directive.startsWith("Domain")) {
					domain = directive.split("=")[1];
				}
				if (directive.startsWith("Max-Age")) {
					maxAge = Integer.parseInt(directive.split("=")[1]);
				}
				if (directive.startsWith("Path")) {
					path = directive.split("=")[1];
				}
				if (directive.startsWith("Secure")) {
					secure = true;
				}
				if (directive.startsWith("HttpOnly")) {
					httpOnly = true;
				}
				if (directive.startsWith("SameSite")) {
					sameSite = directive.split("=")[1];
				}
			}
			responseCookies.add(ResponseCookie.from(name, value).maxAge(maxAge).path(path)
					.domain(domain).secure(secure).httpOnly(httpOnly).sameSite(sameSite)
					.build());
		}
		return responseCookies;
	}

}
