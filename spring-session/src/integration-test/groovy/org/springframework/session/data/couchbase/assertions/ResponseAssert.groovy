package org.springframework.session.data.couchbase.assertions

import org.springframework.http.ResponseEntity

class ResponseAssert {

    private ResponseEntity actual

    protected ResponseAssert(ResponseEntity actual) {
        assert actual != null
        this.actual = actual
    }

    ResponseAssert hasSessionIds(String... sessionIds) {
        hasElements(sessionIds)
    }

    ResponseAssert hasSessionAttributeNames(String... attributeNames) {
        return hasElements(attributeNames)
    }

    ResponseAssert hasNoSessionIds() {
        assert actual.body != null
        def actualSessionIds = actual.body as Set
        assert actualSessionIds.isEmpty()
        return this
    }

    ResponseAssert hasBody(Object body) {
        assert actual.body == body
        return this
    }

    ResponseAssert hasNoBody() {
        assert actual.body == null
        return this
    }

    private ResponseAssert hasElements(String... elements) {
        assert actual.body != null
        def actualSessionIds = actual.body as List
        assert actualSessionIds.size() == elements.size()
        assert actualSessionIds.containsAll(elements)
        return this
    }
}
