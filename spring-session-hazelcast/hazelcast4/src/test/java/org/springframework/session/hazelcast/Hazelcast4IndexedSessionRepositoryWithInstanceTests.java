package org.springframework.session.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository.HazelcastSession;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class Hazelcast4IndexedSessionRepositoryWithInstanceTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String SESSION_MAP_NAME = "spring:session:sessions";

	private HazelcastInstance hzInstance;

	private Hazelcast4IndexedSessionRepository repository;

	@Before
	public void initialize() {
		this.hzInstance = Hazelcast.newHazelcastInstance(new Config());

		this.repository = new Hazelcast4IndexedSessionRepository(this.hzInstance);
		this.repository.setFlushMode(FlushMode.IMMEDIATE);
		this.repository.setSaveMode(SaveMode.ALWAYS);
		this.repository.setSessionMapName(SESSION_MAP_NAME);
		this.repository.init();
	}

	@Test
	public void hazelcastInstance() {
		assertThat(hzInstance).withFailMessage("HazelcastInstance must not be null").isNotNull();
	}

	@Test
	public void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
		String principal = "username";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "notused",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContextImpl securityCtx = new SecurityContextImpl(authentication);

		HazelcastSession newSession = repository.createSession();
		newSession.setAttribute(SPRING_SECURITY_CONTEXT, securityCtx);

		IMap<String, MapSession> sessionsMap = this.hzInstance.getMap(SESSION_MAP_NAME);

		assertThat(sessionsMap).withFailMessage("SessionsMap is empty")
				.size().isGreaterThan(0);

		assertThat(sessionsMap).withFailMessage("SessionEntry does not contain the expected attributes")
				.hasValueSatisfying(new Condition<>(session ->
						session.getAttributeNames().containsAll(Arrays.asList(
								"org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME",
								"SPRING_SECURITY_CONTEXT"
						)), "SessionHasExpectedAttributes"));
	}

}
