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

package sample.mvc;

import java.security.Principal;
import java.util.Collection;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Controller for sending the user to the login view.
 *
 * @author Rob Winch
 *
 */
@Controller
public class IndexController {
	// tag::findbyusername[]
	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	@RequestMapping("/")
	public String index(Principal principal, Model model) {
		Collection<? extends Session> usersSessions = this.sessions
				.findByPrincipalName(principal.getName()).values();
		model.addAttribute("sessions", usersSessions);
		return "index";
	}
	// end::findbyusername[]

	@RequestMapping(value = "/sessions/{sessionIdToDelete}", method = RequestMethod.DELETE)
	public String removeSession(Principal principal,
			@PathVariable String sessionIdToDelete) {
		Set<String> usersSessionIds = this.sessions
				.findByPrincipalName(principal.getName()).keySet();
		if (usersSessionIds.contains(sessionIdToDelete)) {
			this.sessions.deleteById(sessionIdToDelete);
		}

		return "redirect:/";
	}
}
