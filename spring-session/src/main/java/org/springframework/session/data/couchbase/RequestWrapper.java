package org.springframework.session.data.couchbase;

import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Map.Entry;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.session.data.couchbase.CouchbaseSession.globalAttributeName;
import static org.springframework.session.data.couchbase.CouchbaseSessionRepository.GLOBAL_NAMESPACE;
import static org.springframework.util.Assert.notNull;

public class RequestWrapper extends HttpServletRequestWrapper {

    public final String CURRENT_SESSION_ATTR = HttpServletRequestWrapper.class.getName();

    private static final Logger log = getLogger(RequestWrapper.class);

    protected final CouchbaseDao dao;
    protected final String namespace;
    protected final Serializer serializer;

    public RequestWrapper(HttpServletRequest request, CouchbaseDao dao, String namespace, Serializer serializer) {
        super(request);
        this.dao = dao;
        this.namespace = namespace;
        this.serializer = serializer;
    }

    public String changeSessionId() {
        SessionDocument oldDocument = dao.findById(getRequestedSessionId());
        notNull(oldDocument, "Cannot change HTTP session ID, because session with ID '" + getRequestedSessionId() + "' does not exist");

        removeAttribute(CURRENT_SESSION_ATTR);
        dao.delete(oldDocument.getId());

        HttpSession newSession = getSession();
        SessionDocument newDocument = new SessionDocument(newSession.getId(), oldDocument.getData());
        dao.save(newDocument);

        copyGlobalAttributes(oldDocument, newSession);
        copyNamespaceAttributes(oldDocument, newSession);

        log.debug("HTTP session ID changed from {} to {}", oldDocument.getId(), newDocument.getId());

        return newDocument.getId();
    }

    protected void copyGlobalAttributes(SessionDocument oldDocument, HttpSession newSession) {
        Map<String, Object> attributes = oldDocument.getData().get(GLOBAL_NAMESPACE);
        if (attributes != null) {
            Map<String, Object> deserializedAttributes = serializer.deserializeSessionAttributes(attributes);
            for (Entry<String, Object> attribute : deserializedAttributes.entrySet()) {
                newSession.setAttribute(globalAttributeName(attribute.getKey()), attribute.getValue());
            }
        }
    }

    protected void copyNamespaceAttributes(SessionDocument oldDocument, HttpSession newSession) {
        Map<String, Object> attributes = oldDocument.getData().get(namespace);
        if (attributes != null) {
            Map<String, Object> deserializedAttributes = serializer.deserializeSessionAttributes(attributes);
            for (Entry<String, Object> attribute : deserializedAttributes.entrySet()) {
                newSession.setAttribute(attribute.getKey(), attribute.getValue());
            }
        }
    }
}
