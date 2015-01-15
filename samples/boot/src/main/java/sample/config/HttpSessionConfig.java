package sample.config;

import org.springframework.session.data.redis.config.annotation.web.http.*;

@EnableRedisHttpSession // <1>
public class HttpSessionConfig { }
