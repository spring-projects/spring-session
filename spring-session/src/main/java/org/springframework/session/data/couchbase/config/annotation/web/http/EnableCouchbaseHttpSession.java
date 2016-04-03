package org.springframework.session.data.couchbase.config.annotation.web.http;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Configuration
@Import(CouchbaseHttpSessionConfiguration.class)
public @interface EnableCouchbaseHttpSession {

    String namespace();

    int timeoutInSeconds() default 1800;

    boolean principalSessionsEnabled() default false;
}
