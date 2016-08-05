/*
 * The MIT License
 *
 * Copyright 2016 cambierr.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.cambierr.spring.session.couchbase;

import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.repository.annotation.Field;
import com.couchbase.client.java.repository.annotation.Id;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.session.ExpiringSession;

/**
 *
 * @author cambierr
 */
@Document
public class CouchbaseSession implements ExpiringSession {

    public static final String COUCHBASE_PREFIX = "spring::session::";
    public static final int SESSION_DEFAULT_VALIDITY = 1800;
    protected static int SESSION_VALIDITY = SESSION_DEFAULT_VALIDITY;

    @Id
    private String id;
    @Field
    private long created;
    @Field
    private long accessed;
    @Field
    private int interval;
    @Field
    private Map<String, Object> data;
    
    public static void setSessionValidity(int validity){
        SESSION_VALIDITY = validity;
    }

    public CouchbaseSession() {
        this(SESSION_DEFAULT_VALIDITY);
    }

    protected CouchbaseSession(String id) {
        this.id = id;
    }

    public CouchbaseSession(int interval) {
        this(UUID.randomUUID().toString(), interval);
    }

    public CouchbaseSession(String _id, int interval) {
        this.id = _id;
        this.interval = interval;
        this.accessed = System.currentTimeMillis();
        this.data = new HashMap<>();
    }

    @Override
    public long getCreationTime() {
        return this.created;
    }

    @Override
    public void setLastAccessedTime(long lastAccessedTime) {
        this.accessed = lastAccessedTime;
    }

    @Override
    public long getLastAccessedTime() {
        return this.accessed;
    }

    @Override
    public void setMaxInactiveIntervalInSeconds(int interval) {
        this.interval = interval;
    }

    @Override
    public int getMaxInactiveIntervalInSeconds() {
        return this.interval;
    }

    @Override
    public boolean isExpired() {
        return System.currentTimeMillis() > (this.accessed + 1000 * this.interval);
    }

    @Override
    public String getId() {
        return this.id;
    }

    protected long getCouchbaseExpiration() {
        return (this.interval > (86400 * 30)) ? System.currentTimeMillis() / 1000 + this.interval : this.interval;
    }

    @Override
    public <T> T getAttribute(String attributeName) {
        if (!this.data.containsKey(attributeName)) {
            return null;
        }
        Object o = this.data.get(attributeName);
        if (isSerialized(o)) {
            return (T) deserialize((SerializedItem) o);
        } else {
            return (T) o;
        }
    }

    @Override
    public Set<String> getAttributeNames() {
        return this.data.keySet();
    }

    private boolean isTypeValid(Object _object) {
        return _object == null
                || _object instanceof String
                || _object instanceof Integer
                || _object instanceof Long
                || _object instanceof Double
                || _object instanceof Boolean
                || _object instanceof JsonObject
                || _object instanceof JsonArray;
    }

    @Override
    public void setAttribute(String attributeName, Object attributeValue) {
        if (isTypeValid(attributeValue)) {
            this.data.put(attributeName, attributeValue);
        } else {
            this.data.put(attributeName, serialize(attributeValue));
        }
    }

    @Override
    public void removeAttribute(String attributeName) {
        this.data.remove(attributeName);
    }

    private SerializedItem serialize(Object _object) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(_object);
            oos.close();
            return new SerializedItem(Base64.getEncoder().encodeToString(baos.toByteArray()));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Object deserialize(SerializedItem _serialized) {
        if (_serialized == null) {
            return null;
        }
        try {
            return new ObjectInputStream(
                    new ByteArrayInputStream(
                            Base64.getDecoder().decode(_serialized.serialized)
                    )
            ).readObject();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean isSerialized(Object _object) {
        return _object instanceof SerializedItem;
    }

    public static class SerializedItem {

        public String serialized;

        public SerializedItem() {

        }

        public SerializedItem(String _serialized) {
            this.serialized = _serialized;
        }
    }

}
