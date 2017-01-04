package org.springframework.session.web.http;

import javax.servlet.http.HttpServletResponse;

/**
 * Allows ensuring that the session is saved if the response is committed.
 *
 * @author Rob Winch
 * @since 1.0
 */
public final class SessionRepositoryResponseWrapper extends OnCommittedResponseWrapper {
// ------------------------------ FIELDS ------------------------------

    private final SessionRepositoryRequestWrapper request;

// --------------------------- CONSTRUCTORS ---------------------------


    /**
     * Create
     * @param request Wrapper rest
     * @param response Http Response
     */
    public SessionRepositoryResponseWrapper(SessionRepositoryRequestWrapper request, HttpServletResponse response) {
        super(response);
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        this.request = request;
    }

// -------------------------- OTHER METHODS --------------------------

    @Override
    protected void onResponseCommitted() {
        request.commitSession();
    }
}