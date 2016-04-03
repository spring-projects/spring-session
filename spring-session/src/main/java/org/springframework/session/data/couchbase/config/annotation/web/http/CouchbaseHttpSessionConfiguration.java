package org.springframework.session.data.couchbase.config.annotation.web.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.couchbase.CouchbaseDao;
import org.springframework.session.data.couchbase.CouchbaseSessionRepository;
import org.springframework.session.data.couchbase.DelegatingSessionStrategy;
import org.springframework.session.data.couchbase.Serializer;
import org.springframework.session.web.http.CookieHttpSessionStrategy;
import org.springframework.session.web.http.MultiHttpSessionStrategy;

import java.util.Map;

@Configuration
@EnableSpringHttpSession
public class CouchbaseHttpSessionConfiguration implements ImportAware {

    protected String namespace;
    protected int timeoutInSeconds;
    protected boolean principalSessionsEnabled;

    @Bean
    public CouchbaseDao couchbaseDao(CouchbaseTemplate couchbase) {
        return new CouchbaseDao(couchbase);
    }

    @Bean
    public MultiHttpSessionStrategy multiHttpSessionStrategy(CouchbaseDao dao, Serializer serializer) {
        return new DelegatingSessionStrategy(new CookieHttpSessionStrategy(), dao, namespace, serializer);
    }

    @Bean
    public Serializer serializer() {
        return new Serializer();
    }

    @Bean
    public SessionRepository sessionRepository(CouchbaseDao dao, ObjectMapper mapper, Serializer serializer) {
        return new CouchbaseSessionRepository(dao, namespace, mapper, timeoutInSeconds, serializer, principalSessionsEnabled);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> attributesByNames = importMetadata.getAnnotationAttributes(EnableCouchbaseHttpSession.class.getName());
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributesByNames);
        namespace = attributes.getString("namespace");
        timeoutInSeconds = attributes.getNumber("timeoutInSeconds");
        principalSessionsEnabled = attributes.getBoolean("principalSessionsEnabled");
    }
}
