package org.springframework.session.data.couchbase;

import com.couchbase.client.java.repository.annotation.Field;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

import java.util.Map;

@Document
public class SessionDocument {

    @Id
    protected final String id;
    @Field
    protected final Map<String, Map<String, Object>> data;

    public SessionDocument(String id, Map<String, Map<String, Object>> data) {
        this.id = id;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public Map<String, Map<String, Object>> getData() {
        return data;
    }
}
