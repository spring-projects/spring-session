package org.springframework.session.data.redis;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.CREATION_TIME_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.LAST_ACCESSED_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.MAX_INACTIVE_ATTR;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.getKey;
import static org.springframework.session.data.redis.RedisOperationsSessionRepository.getSessionAttrNameKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;
import org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession;


@RunWith(MockitoJUnitRunner.class)
public class RedisOperationsSessionRepositoryTests {
    @Mock
    RedisConnectionFactory factory;
    @Mock
    RedisConnection connection;
    @Mock
    RedisOperations<String,ExpiringSession> redisOperations;
    @Mock
    RedisOperations<String,String> expirationRedisOperations;
    @Mock
    BoundHashOperations<String, Object, Object> boundHashOperations;
    @Mock
    BoundSetOperations<String, String> boundSetOperations;
    @Captor
    ArgumentCaptor<Map<String,Object>> delta;
    @Captor
    ArgumentCaptor<Set<String>> keys;

    private RedisOperationsSessionRepository redisRepository;

    @Before
    public void setup() {
        this.redisRepository = new RedisOperationsSessionRepository(redisOperations,expirationRedisOperations);
    }

    @Test(expected=IllegalArgumentException.class)
    public void constructorNullConnectionFactory() {
        new RedisOperationsSessionRepository(null);
    }

    // gh-61
    @Test
    public void constructorConnectionFactory() {
        redisRepository = new RedisOperationsSessionRepository(factory);
        RedisSession session = redisRepository.createSession();

        when(factory.getConnection()).thenReturn(connection);

        redisRepository.save(session);
    }

    @Test
    public void createSessionDefaultMaxInactiveInterval() throws Exception {
        ExpiringSession session = redisRepository.createSession();
        assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
    }

    @Test
    public void createSessionCustomMaxInactiveInterval() throws Exception {
        int interval = 1;
        redisRepository.setDefaultMaxInactiveInterval(interval);
        ExpiringSession session = redisRepository.createSession();
        assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(interval);
    }

    @Test
    public void saveNewSession() {
        RedisSession session = redisRepository.createSession();
        when(redisOperations.boundHashOps(getKey(session.getId()))).thenReturn(boundHashOperations);
        when(expirationRedisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

        redisRepository.save(session);

        Map<String,Object> delta = getDelta();
        assertThat(delta.size()).isEqualTo(3);
        Object creationTime = delta.get(CREATION_TIME_ATTR);
        assertThat(creationTime).isInstanceOf(Long.class);
        assertThat(delta.get(MAX_INACTIVE_ATTR)).isEqualTo(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
        assertThat(delta.get(LAST_ACCESSED_ATTR)).isEqualTo(creationTime);
    }

    @Test
    public void saveLastAccessChanged() {
        RedisSession session = redisRepository.new RedisSession(new MapSession());
        session.setLastAccessedTime(12345678L);
        when(redisOperations.boundHashOps(getKey(session.getId()))).thenReturn(boundHashOperations);
        when(expirationRedisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

        redisRepository.save(session);

        assertThat(getDelta()).isEqualTo(map(LAST_ACCESSED_ATTR, session.getLastAccessedTime()));
    }

    @Test
    public void saveSetAttribute() {
        String attrName = "attrName";
        RedisSession session = redisRepository.new RedisSession(new MapSession());
        session.setAttribute(attrName, "attrValue");
        when(redisOperations.boundHashOps(getKey(session.getId()))).thenReturn(boundHashOperations);
        when(expirationRedisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

        redisRepository.save(session);

        assertThat(getDelta()).isEqualTo(map(getSessionAttrNameKey(attrName), session.getAttribute(attrName)));
    }

    @Test
    public void saveRemoveAttribute() {
        String attrName = "attrName";
        RedisSession session = redisRepository.new RedisSession(new MapSession());
        session.removeAttribute(attrName);
        when(redisOperations.boundHashOps(getKey(session.getId()))).thenReturn(boundHashOperations);
        when(expirationRedisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

        redisRepository.save(session);

        assertThat(getDelta()).isEqualTo(map(getSessionAttrNameKey(attrName), null));
    }

    @Test
    public void redisSessionGetAttributes() {
        String attrName = "attrName";
        RedisSession session = redisRepository.new RedisSession(new MapSession());
        assertThat(session.getAttributeNames()).isEmpty();
        session.setAttribute(attrName, "attrValue");
        assertThat(session.getAttributeNames()).containsOnly(attrName);
        session.removeAttribute(attrName);
        assertThat(session.getAttributeNames()).isEmpty();
    }

    @Test
    public void delete() {
        String attrName = "attrName";
        MapSession expected = new MapSession();
        expected.setLastAccessedTime(System.currentTimeMillis() - 60000);
        expected.setAttribute(attrName, "attrValue");
        when(redisOperations.boundHashOps(getKey(expected.getId()))).thenReturn(boundHashOperations);
        Map map = map(
                getSessionAttrNameKey(attrName), expected.getAttribute(attrName),
                CREATION_TIME_ATTR, expected.getCreationTime(),
                MAX_INACTIVE_ATTR, expected.getMaxInactiveIntervalInSeconds(),
                LAST_ACCESSED_ATTR, expected.getLastAccessedTime());
        when(boundHashOperations.entries()).thenReturn(map);
        when(expirationRedisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);

        String id = expected.getId();
        redisRepository.delete(id);
        verify(redisOperations).delete(getKey(id));
    }

    @Test
    public void deleteNullSession() {
        when(expirationRedisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
        when(redisOperations.boundHashOps(anyString())).thenReturn(boundHashOperations);

        String id = "abc";
        redisRepository.delete(id);
        verify(redisOperations,times(0)).delete(anyString());
        verify(expirationRedisOperations,times(0)).delete(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSessionNotFound() {
        String id = "abc";
        when(redisOperations.boundHashOps(getKey(id))).thenReturn(boundHashOperations);
        when(boundHashOperations.entries()).thenReturn(map());

        assertThat(redisRepository.getSession(id)).isNull();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSessionFound() {
        String attrName = "attrName";
        MapSession expected = new MapSession();
        expected.setLastAccessedTime(System.currentTimeMillis() - 60000);
        expected.setAttribute(attrName, "attrValue");
        when(redisOperations.boundHashOps(getKey(expected.getId()))).thenReturn(boundHashOperations);
        Map map = map(
                getSessionAttrNameKey(attrName), expected.getAttribute(attrName),
                CREATION_TIME_ATTR, expected.getCreationTime(),
                MAX_INACTIVE_ATTR, expected.getMaxInactiveIntervalInSeconds(),
                LAST_ACCESSED_ATTR, expected.getLastAccessedTime());
        when(boundHashOperations.entries()).thenReturn(map);

        long now = System.currentTimeMillis();
        RedisSession session = redisRepository.getSession(expected.getId());
        assertThat(session.getId()).isEqualTo(expected.getId());
        assertThat(session.getAttributeNames()).isEqualTo(expected.getAttributeNames());
        assertThat(session.getAttribute(attrName)).isEqualTo(expected.getAttribute(attrName));
        assertThat(session.getCreationTime()).isEqualTo(expected.getCreationTime());
        assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expected.getMaxInactiveIntervalInSeconds());
        assertThat(session.getLastAccessedTime()).isGreaterThanOrEqualTo(now);

    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSessionExpired() {
        String expiredId = "expired-id";
        when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
        Map map = map(
                MAX_INACTIVE_ATTR, 1,
                LAST_ACCESSED_ATTR, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
        when(boundHashOperations.entries()).thenReturn(map);

        assertThat(redisRepository.getSession(expiredId)).isNull();
    }

    @Test
    public void cleanupExpiredSessions() {
        String expiredId = "expired-id";
        when(redisOperations.boundHashOps(getKey(expiredId))).thenReturn(boundHashOperations);
        when(expirationRedisOperations.boundSetOps(anyString())).thenReturn(boundSetOperations);
        Set<String> expiredIds = new HashSet<String>(Arrays.asList("expired-key1","expired-key2"));
        when(boundSetOperations.members()).thenReturn(expiredIds);

        redisRepository.cleanupExpiredSessions();

        verify(redisOperations).delete(keys.capture());

        for(String id : expiredIds) {
            String expiredKey = RedisOperationsSessionRepository.BOUNDED_HASH_KEY_PREFIX + id;
            assertThat(keys.getValue()).contains(expiredKey);
        }
        // the key that maps the expiration time to the expired ids
        assertThat(keys.getValue().size()).isEqualTo(expiredIds.size() + 1);
    }

    @SuppressWarnings("rawtypes")
    private Map map(Object...objects) {
        Map<String,Object> result = new HashMap<String,Object>();
        if(objects == null) {
            return result;
        }
        for(int i = 0; i < objects.length; i += 2) {
            result.put((String)objects[i], objects[i+1]);
        }
        return result;
    }

    private Map<String,Object> getDelta() {
        verify(boundHashOperations).putAll(delta.capture());
        return delta.getValue();
    }
}