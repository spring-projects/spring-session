/*
 * Copyright 2014-present the original author or authors.
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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.util.UUID;

import com.redis.testcontainers.RedisContainer;
import io.lettuce.core.ReadFrom;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;

import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link RedisIndexedSessionRepository} using
 * {@link RedisStaticMasterReplicaConfiguration}.
 */
class RedisIndexedSessionRepositoryStaticMasterReplicaITests {

	private static final String INDEX_NAME = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Test // gh-1715
	void cleanUpExpiredSessionsWhenStaticMasterReplicaExpirationEventUnavailableThenRemovesPrincipalIndex()
			throws Exception {
		Network network = Network.newNetwork();
		RedisContainer master = redisContainer();
		master.withNetwork(network);
		master.withNetworkAliases("redis-master");
		RedisContainer replica = redisContainer();
		replica.withNetwork(network);
		replica.withCommand("redis-server", "--replicaof", "redis-master", "6379");
		try {
			master.start();
			replica.start();
			awaitReplica(replica);
			LettuceConnectionFactory connectionFactory = createStaticMasterReplicaConnectionFactory(master, replica);
			try {
				connectionFactory.afterPropertiesSet();
				RedisTemplate<String, Object> redis = createRedisTemplate(connectionFactory);
				RedisIndexedSessionRepository repository = new RedisIndexedSessionRepository(redis);
				String namespace = "RedisIndexedSessionRepositoryStaticMasterReplicaITests" + UUID.randomUUID();
				repository.setRedisKeyNamespace(namespace);
				repository.setDefaultMaxInactiveInterval(Duration.ofSeconds(1));

				String principalName = "findByPrincipalNameStaticMasterReplica" + UUID.randomUUID();
				RedisSession session = repository.createSession();
				session.setAttribute(INDEX_NAME, principalName);

				repository.save(session);

				String sessionKey = namespace + ":sessions:" + session.getId();
				String expiresKey = namespace + ":sessions:expires:" + session.getId();
				String principalIndexKey = namespace + ":index:" + INDEX_NAME + ":" + principalName;
				awaitReplicaContainsSessionAndIndex(replica, sessionKey, principalIndexKey);
				assertThat(repository.findByIndexNameAndIndexValue(INDEX_NAME, principalName)).hasSize(1);

				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
					assertThat(redis.hasKey(expiresKey)).isFalse();
					assertThat(repository.findById(session.getId())).isNull();
				});

				assertThat(redis.boundSetOps(principalIndexKey).members()).contains(session.getId());
				assertThat(repository.findByIndexNameAndIndexValue(INDEX_NAME, principalName)).isEmpty();

				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
					String expirationKey = namespace + ":expirations:" + roundDownMinute(System.currentTimeMillis());
					redis.boundSetOps(expirationKey).add("expires:" + session.getId());
					repository.cleanUpExpiredSessions();
					assertThat(redis.boundSetOps(principalIndexKey).members()).doesNotContain(session.getId());
				});
			}
			finally {
				connectionFactory.destroy();
			}
		}
		finally {
			replica.stop();
			master.stop();
			network.close();
		}
	}

	private static RedisContainer redisContainer() {
		return new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));
	}

	private static long roundDownMinute(long timeInMillis) {
		long minuteInMillis = Duration.ofMinutes(1).toMillis();
		return timeInMillis - (timeInMillis % minuteInMillis);
	}

	private static LettuceConnectionFactory createStaticMasterReplicaConnectionFactory(RedisContainer master,
			RedisContainer replica) {
		RedisStaticMasterReplicaConfiguration configuration = new RedisStaticMasterReplicaConfiguration(
				master.getHost(), master.getFirstMappedPort());
		configuration.node(replica.getHost(), replica.getFirstMappedPort());
		LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
			.readFrom(ReadFrom.REPLICA_PREFERRED)
			.commandTimeout(Duration.ofSeconds(5))
			.build();
		return new LettuceConnectionFactory(configuration, clientConfiguration);
	}

	private static RedisTemplate<String, Object> createRedisTemplate(LettuceConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setKeySerializer(RedisSerializer.string());
		redisTemplate.setHashKeySerializer(RedisSerializer.string());
		redisTemplate.setConnectionFactory(connectionFactory);
		redisTemplate.afterPropertiesSet();
		return redisTemplate;
	}

	private static void awaitReplica(RedisContainer replica) {
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			Container.ExecResult result = replica.execInContainer("redis-cli", "INFO", "replication");
			String output = result.getStdout();
			assertThat(output).contains("master_link_status:up");
			assertThat(output.contains("role:slave") || output.contains("role:replica")).isTrue();
		});
	}

	private static void awaitReplicaContainsSessionAndIndex(RedisContainer replica, String sessionKey,
			String principalIndexKey) {
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			Container.ExecResult sessionResult = replica.execInContainer("redis-cli", "EXISTS", sessionKey);
			Container.ExecResult indexResult = replica.execInContainer("redis-cli", "SCARD", principalIndexKey);
			assertThat(sessionResult.getStdout().trim()).isEqualTo("1");
			assertThat(indexResult.getStdout().trim()).isEqualTo("1");
		});
	}

}
