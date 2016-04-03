package org.springframework.session.data.couchbase.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.couchbase.CouchbaseDao;
import org.springframework.session.data.couchbase.CouchbaseSessionRepository;
import org.springframework.session.data.couchbase.Serializer;
import org.springframework.session.data.couchbase.config.annotation.web.http.CouchbaseHttpSessionConfiguration;

@Configuration
public class SessionConfiguration extends CouchbaseHttpSessionConfiguration {

    public static final String HTTP_SESSION_NAMESPACE = "test-application";

    @Bean
    public SessionRepository sessionRepository(CouchbaseDao dao, ObjectMapper mapper, Serializer serializer) {
        return new CouchbaseSessionRepository(dao, namespace, mapper, timeoutInSeconds, serializer, principalSessionsEnabled) {

            @Override
            protected int getSessionDocumentExpiration() {
                return timeoutInSeconds;
            }
        };
    }
}
