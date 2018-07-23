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

package sample.session;

import java.io.IOException;
import java.net.InetAddress;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Inserts the session details into the session for every request. Some users may prefer
 * to insert session details only after authentication. This is fine, but it may be
 * valuable to the most up to date information so that if someone stole the user's session
 * id it can be observed.
 *
 * @author Rob Winch
 *
 */
// tag::class[]
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 101)
public class SessionDetailsFilter extends OncePerRequestFilter {
	static final String UNKNOWN = "Unknown";

	private DatabaseReader reader;

	@Autowired
	public SessionDetailsFilter(DatabaseReader reader) {
		this.reader = reader;
	}

	// tag::dofilterinternal[]
	@Override
	public void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		chain.doFilter(request, response);

		HttpSession session = request.getSession(false);
		if (session != null) {
			String remoteAddr = getRemoteAddress(request);
			String geoLocation = getGeoLocation(remoteAddr);

			SessionDetails details = new SessionDetails();
			details.setAccessType(request.getHeader("User-Agent"));
			details.setLocation(remoteAddr + " " + geoLocation);

			session.setAttribute("SESSION_DETAILS", details);
		}
	}
	// end::dofilterinternal[]

	String getGeoLocation(String remoteAddr) {
		try {
			CityResponse city = this.reader.city(InetAddress.getByName(remoteAddr));
			String cityName = city.getCity().getName();
			String countryName = city.getCountry().getName();
			if (cityName == null && countryName == null) {
				return null;
			}
			else if (cityName == null) {
				return countryName;
			}
			else if (countryName == null) {
				return cityName;
			}
			return cityName + ", " + countryName;
		}
		catch (Exception ex) {
			return UNKNOWN;

		}
	}

	private String getRemoteAddress(HttpServletRequest request) {
		String remoteAddr = request.getHeader("X-FORWARDED-FOR");
		if (remoteAddr == null) {
			remoteAddr = request.getRemoteAddr();
		}
		else if (remoteAddr.contains(",")) {
			remoteAddr = remoteAddr.split(",")[0];
		}
		return remoteAddr;
	}
}
// end::class[]
