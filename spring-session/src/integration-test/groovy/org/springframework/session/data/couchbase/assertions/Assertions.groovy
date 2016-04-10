package org.springframework.session.data.couchbase.assertions

import org.springframework.http.ResponseEntity

class Assertions {

    static ResponseAssert assertThat(ResponseEntity response) {
        return new ResponseAssert(response)
    }
}
