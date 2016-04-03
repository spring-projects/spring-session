package org.springframework.session.data.couchbase.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.couchbase.config.annotation.web.http.EnableCouchbaseHttpSession;

@SpringBootApplication
@EnableCouchbaseHttpSession(namespace = SessionConfiguration.HTTP_SESSION_NAMESPACE, principalSessionsEnabled = true)
public class PrincipalSessionsTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrincipalSessionsTestApplication.class, args);
    }
}
