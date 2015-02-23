package sample;

import org.springframework.session.data.redis.config.annotation.web.http.*;

@EnableRedisHttpSession // <1>
public class RedisHttpSessionConfig {}
