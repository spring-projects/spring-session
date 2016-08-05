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

import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.util.Assert;

/**
 *
 * @author cambierr
 */
public class CouchbaseSessionRepository implements SessionRepository<CouchbaseSession> {

    private final CouchbaseTemplate couchbase;

    public CouchbaseSessionRepository(CouchbaseTemplate couchbase) {
        Assert.notNull(couchbase);
        this.couchbase = couchbase;
    }

    @Override
    public CouchbaseSession createSession() {
        return new CouchbaseSession();
    }

    @Override
    public void save(CouchbaseSession session) {
        couchbase.save(session);
    }

    @Override
    public CouchbaseSession getSession(String id) {
        return couchbase.findById(id, CouchbaseSession.class);
    }

    @Override
    public void delete(String id) {
        couchbase.remove(new CouchbaseSession(id));
    }

}
