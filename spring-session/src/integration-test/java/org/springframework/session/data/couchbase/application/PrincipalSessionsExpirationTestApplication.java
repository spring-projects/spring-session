package org.springframework.session.data.couchbase.application;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.couchbase.config.annotation.web.http.EnableCouchbaseHttpSession;

import static org.springframework.boot.SpringApplication.run;
import static org.springframework.session.data.couchbase.application.SessionConfiguration.HTTP_SESSION_NAMESPACE;

@SpringBootApplication
@EnableCouchbaseHttpSession(namespace = HTTP_SESSION_NAMESPACE, principalSessionsEnabled = true, timeoutInSeconds = 1)
public class PrincipalSessionsExpirationTestApplication {

    public static void main(String[] args) {
        run(PrincipalSessionsExpirationTestApplication.class, args);
    }
}
