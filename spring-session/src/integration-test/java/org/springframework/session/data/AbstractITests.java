package org.springframework.session.data;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.UUID;

/**
 * Base class for repositories integration tests
 *
 * @author Jakub Kubrynski
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public abstract class AbstractITests {

    @Autowired
    protected SessionEventRegistry registry;

    protected SecurityContext context;

    protected SecurityContext changedContext;

    @Before
    public void setup() {
        registry.clear();
        context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("username-"+ UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));

        changedContext = SecurityContextHolder.createEmptyContext();
        changedContext.setAuthentication(new UsernamePasswordAuthenticationToken("changedContext-"+UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

}
