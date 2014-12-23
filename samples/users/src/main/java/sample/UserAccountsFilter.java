/*
 * Copyright 2002-2013 the original author or authors.
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
package sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.HttpSessionManager;

public class UserAccountsFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @SuppressWarnings("unchecked")
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // tag::HttpSessionManager[]
        HttpSessionManager sessionManager =
                (HttpSessionManager) httpRequest.getAttribute(HttpSessionManager.class.getName());
        // end::HttpSessionManager[]
        SessionRepository<Session> repo =
                (SessionRepository<Session>) httpRequest.getAttribute(SessionRepository.class.getName());

        String currentSessionAlias = sessionManager.getCurrentSessionAlias(httpRequest);
        Map<String, String> sessionIds = sessionManager.getSessionIds(httpRequest);
        String unauthenticatedAlias = null;

        String contextPath = httpRequest.getContextPath();
        List<Account> accounts = new ArrayList<Account>();
        Account currentAccount = null;
        for(Map.Entry<String, String> entry : sessionIds.entrySet()) {
            String alias = entry.getKey();
            String sessionId = entry.getValue();

            Session session = repo.getSession(sessionId);
            if(session == null) {
                continue;
            }

            String username = session.getAttribute("username");
            if(username == null) {
                unauthenticatedAlias = alias;
                continue;
            }

            String logoutUrl = sessionManager.encodeURL("./logout", alias);
            String switchAccountUrl = sessionManager.encodeURL("./", alias);
            Account account = new Account(username, logoutUrl, switchAccountUrl);
            if(currentSessionAlias.equals(alias)) {
                currentAccount = account;
            } else {
                accounts.add(account);
            }
        }

        // tag::addAccountUrl[]
        String addAlias = unauthenticatedAlias == null ? // <1>
                sessionManager.getNewSessionAlias(httpRequest) : // <2>
                unauthenticatedAlias; // <3>
        String addAccountUrl = sessionManager.encodeURL(contextPath, addAlias); // <4>
        // end::addAccountUrl[]

        httpRequest.setAttribute("currentAccount", currentAccount);
        httpRequest.setAttribute("addAccountUrl", addAccountUrl);
        httpRequest.setAttribute("accounts", accounts);

        chain.doFilter(request, response);
    }

    public void destroy() {
    }

}
