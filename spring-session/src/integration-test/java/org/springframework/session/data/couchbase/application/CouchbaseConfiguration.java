package org.springframework.session.data.couchbase.application;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableCouchbaseRepositories
public class CouchbaseConfiguration extends AbstractCouchbaseConfiguration {

    public static final String COUCHBASE_BUCKET_NAME = "default";

    @Override
    protected List<String> getBootstrapHosts() {
        return Collections.singletonList("localhost");
    }

    @Override
    protected String getBucketName() {
        return COUCHBASE_BUCKET_NAME;
    }

    @Override
    protected String getBucketPassword() {
        return "";
    }
}
