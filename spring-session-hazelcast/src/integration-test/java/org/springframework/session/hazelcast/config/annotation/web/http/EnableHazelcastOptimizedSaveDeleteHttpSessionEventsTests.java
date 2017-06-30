package org.springframework.session.hazelcast.config.annotation.web.http;

import com.hazelcast.core.HazelcastInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.hazelcast.HazelcastITestUtils;
import org.springframework.session.hazelcast.SessionEventRegistry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensure that optimized save and delete do not break events contract
 *
 * @author Vishal Dhawani
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = EnableHazelcastOptimizedSaveDeleteHttpSessionEventsTests.HazelcastSessionOptimizedConfig.class
)
@WebAppConfiguration
public class EnableHazelcastOptimizedSaveDeleteHttpSessionEventsTests<S extends Session> {

    private final static int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

    @Autowired
    private SessionRepository<S> repository;

    @Autowired
    private SessionEventRegistry registry;

    @Before
    public void setup() {
        this.registry.clear();
    }

    @Test
    public void saveSessionTest() throws InterruptedException {
        String username = "saves-" + System.currentTimeMillis();

        S sessionToSave = this.repository.createSession();

        String expectedAttributeName = "a";
        String expectedAttributeValue = "b";
        sessionToSave.setAttribute(expectedAttributeName, expectedAttributeValue);
        Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username,
                "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
        toSaveContext.setAuthentication(toSaveToken);
        sessionToSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);
        sessionToSave.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
                .isInstanceOf(SessionCreatedEvent.class);

        Session session = this.repository.findById(sessionToSave.getId());

        assertThat(session.getId()).isEqualTo(sessionToSave.getId());
        assertThat(session.getAttributeNames())
                .isEqualTo(sessionToSave.getAttributeNames());
        assertThat(session.<String>getAttribute(expectedAttributeName))
                .isEqualTo(sessionToSave.getAttribute(expectedAttributeName));
    }

    @Test
    public void expiredSessionTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();

        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
                .isInstanceOf(SessionCreatedEvent.class);
        this.registry.clear();

        assertThat(sessionToSave.getMaxInactiveInterval())
                .isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToSave.getId()))
                .isInstanceOf(SessionExpiredEvent.class);
        assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToSave.getId()).<Session>getSession())
                .isNull();

        assertThat(this.repository.<Session>findById(sessionToSave.getId())).isNull();
    }

    @Test
    public void deletedSessionTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();

        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
                .isInstanceOf(SessionCreatedEvent.class);
        this.registry.clear();

        this.repository.deleteById(sessionToSave.getId());

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionDeletedEvent>getEvent(sessionToSave.getId()))
                .isInstanceOf(SessionDeletedEvent.class);
        assertThat(this.registry.<SessionDeletedEvent>getEvent(sessionToSave.getId()).<Session>getSession())
                .isNull();

        assertThat(this.repository.findById(sessionToSave.getId())).isNull();
    }

    @Configuration
    @EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS, optimizeDelete = true, optimizeSave = true)
    static class HazelcastSessionOptimizedConfig {
        @Bean
        public HazelcastInstance embeddedHazelcast() {
            return HazelcastITestUtils.embeddedHazelcastServer();
        }

        @Bean
        public SessionEventRegistry sessionEventRegistry() {
            return new SessionEventRegistry();
        }
    }
}
