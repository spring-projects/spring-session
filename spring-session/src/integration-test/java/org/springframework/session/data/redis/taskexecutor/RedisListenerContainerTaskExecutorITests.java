package org.springframework.session.data.redis.taskexecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Vladimir Tsanev
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class RedisListenerContainerTaskExecutorITests {

    @Autowired
    SessionTaskExecutor executor;

    @Autowired
    RedisOperations<Object, Object> redis;

    private final Object lock = new Object();

    @Before
    public void setup() {
        executor.setLock(lock);
    }


    @Test
    public void testRedisDelEventsAreDispatchedInSessionTaskExecutor() throws InterruptedException {
        BoundSetOperations<Object, Object> ops =
                        redis.boundSetOps("spring:session:RedisListenerContainerTaskExecutorITests:expirations:dummy");
        ops.add("value");
        ops.remove("value");
        synchronized (lock) {
            lock.wait(TimeUnit.SECONDS.toMillis(1));
        }
        assertThat(executor.taskDispatched()).isTrue();

    }

    static class SessionTaskExecutor implements TaskExecutor {
        private Object lock;
        private final Executor executor;

        private boolean taskDispatched;

        public SessionTaskExecutor(Executor executor) {
            this.executor = executor;
        }

        public void setLock(Object lock) {
            this.lock = lock;
        }

        @Override
        public void execute(Runnable task) {
            synchronized (lock) {
                try {
                    executor.execute(task);
                } finally {
                    taskDispatched = true;
                    lock.notifyAll();
                }
            }
        }

        public boolean taskDispatched() {
            return taskDispatched;
        }
    }

    @Configuration
    @EnableRedisHttpSession(redisNamespace = "RedisListenerContainerTaskExecutorITests")
	static class Config {

        @Bean
        JedisConnectionFactory connectionFactory() throws Exception {
            JedisConnectionFactory factory = new JedisConnectionFactory();
            factory.setUsePool(false);
            return factory;
        }

		@Bean
        Executor springSessionRedisTaskExecutor() {
            return new SessionTaskExecutor(Executors.newSingleThreadExecutor());
        }

        @Bean
        Executor springSessionRedisSubscriptionExecutor() {
            return new SimpleAsyncTaskExecutor();
        }
	}
}
