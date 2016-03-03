package org.springframework.session.web.http;

import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link javax.servlet.http.HttpServletRequest} that retrieves the {@link javax.servlet.http.HttpSession} using a
 * {@link org.springframework.session.SessionRepository}.
 *
 * @author Rob Winch
 * @since 1.0
 */
public final class SessionRepositoryRequestWrapper<S extends ExpiringSession> extends HttpServletRequestWrapper {
// ------------------------------ FIELDS ------------------------------

    private final String CURRENT_SESSION_ATTR = HttpServletRequestWrapper.class.getName();
    private final HttpServletResponse response;
    private final ServletContext servletContext;
    private final SessionRepository<S> sessionRepository;
    private final HttpSessionStrategy httpSessionStrategy;

    private String sessionAttribute = CURRENT_SESSION_ATTR;
    private Boolean requestedSessionIdValid;
    private boolean requestedSessionInvalidated;

// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * teste
     * @param request Request
     * @param response response
     * @param servletContext servlet
     * @param sessionRepository repository
     * @param httpSessionStrategy strategy
     */
    public SessionRepositoryRequestWrapper(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext, SessionRepository<S> sessionRepository, HttpSessionStrategy httpSessionStrategy) {
        super(request);
        this.response = response;
        this.servletContext = servletContext;
        this.sessionRepository = sessionRepository;
        this.httpSessionStrategy = httpSessionStrategy;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public boolean isRequestedSessionIdValid() {
        if (requestedSessionIdValid == null) {
            String sessionId = getRequestedSessionId();
            Session session = sessionId == null ? null : getSession(sessionId);
            return isRequestedSessionIdValid(session);
        }

        return requestedSessionIdValid;
    }

    @Override
    public String getRequestedSessionId() {
        return httpSessionStrategy.getRequestedSessionId(this);
    }

    private S getSession(String sessionId) {
        S session = sessionRepository.getSession(sessionId);
        if (session == null) {
            return null;
        }
        session.setLastAccessedTime(System.currentTimeMillis());
        return session;
    }

    public void setSessionAttribute(String sessionAttribute) {
        this.sessionAttribute = sessionAttribute;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface ServletRequest ---------------------

    public ServletContext getServletContext() {
        if (servletContext != null) {
            return servletContext;
        }
        // Servlet 3.0+
        return super.getServletContext();
    }

// -------------------------- OTHER METHODS --------------------------

    @SuppressWarnings("unused")
    public String changeSessionId() {
        HttpSession session = getSession(false);

        if (session == null) {
            throw new IllegalStateException("Cannot change session ID. There is no session associated with this request.");
        }

        // eagerly get session attributes in case implementation lazily loads them
        Map<String, Object> attrs = new HashMap<String, Object>();
        Enumeration<String> iAttrNames = session.getAttributeNames();
        while (iAttrNames.hasMoreElements()) {
            String attrName = iAttrNames.nextElement();
            Object value = session.getAttribute(attrName);

            attrs.put(attrName, value);
        }

        sessionRepository.delete(session.getId());
        HttpSessionWrapper original = getCurrentSession();
        setCurrentSession(null);

        HttpSessionWrapper newSession = getSession();
        original.setSession(newSession.getSession());

        newSession.setMaxInactiveInterval(session.getMaxInactiveInterval());
        for (Map.Entry<String, Object> attr : attrs.entrySet()) {
            String attrName = attr.getKey();
            Object attrValue = attr.getValue();
            newSession.setAttribute(attrName, attrValue);
        }
        return newSession.getId();
    }

    @Override
    public HttpSessionWrapper getSession(boolean create) {
        HttpSessionWrapper currentSession = getCurrentSession();
        if (currentSession != null) {
            return currentSession;
        }
        String requestedSessionId = getRequestedSessionId();
        if (requestedSessionId != null) {
            S session = getSession(requestedSessionId);
            if (session != null) {
                this.requestedSessionIdValid = true;
                currentSession = new HttpSessionWrapper(session, getServletContext());
                currentSession.setNew(false);
                setCurrentSession(currentSession);
                return currentSession;
            }
        }
        if (!create) {
            return null;
        }

        S session = sessionRepository.createSession();
        session.setLastAccessedTime(System.currentTimeMillis());
        currentSession = new HttpSessionWrapper(session, getServletContext());
        setCurrentSession(currentSession);
        return currentSession;
    }

    @SuppressWarnings("unchecked")
    private HttpSessionWrapper getCurrentSession() {
        return (HttpSessionWrapper) getAttribute(sessionAttribute);
    }

    private void setCurrentSession(HttpSessionWrapper currentSession) {
        if (currentSession == null) {
            removeAttribute(sessionAttribute);
        } else {
            setAttribute(sessionAttribute, currentSession);
        }
    }

    @Override
    public HttpSessionWrapper getSession() {
        return getSession(true);
    }

    /**
     * Uses the HttpSessionStrategy to write the session id tot he response and persist the Session.
     */
    public void commitSession() {
        HttpSessionWrapper wrappedSession = getCurrentSession();
        if (wrappedSession == null) {
            if (isInvalidateClientSession()) {
                httpSessionStrategy.onInvalidateSession(this, response);
            }
        } else {
            S session = wrappedSession.getSession();
            sessionRepository.save(session);
            if (!isRequestedSessionIdValid() || !session.getId().equals(getRequestedSessionId())) {
                httpSessionStrategy.onNewSession(session, this, response);
            }
        }
    }

    private boolean isInvalidateClientSession() {
        return getCurrentSession() == null && requestedSessionInvalidated;
    }

    private boolean isRequestedSessionIdValid(Session session) {
        if (requestedSessionIdValid == null) {
            requestedSessionIdValid = session != null;
        }
        return requestedSessionIdValid;
    }

// -------------------------- INNER CLASSES --------------------------

    /**
     * Allows creating an HttpSession from a Session instance.
     *
     * @author Rob Winch
     * @since 1.0
     */
    private final class HttpSessionWrapper extends ExpiringSessionHttpSession<S> {
        public HttpSessionWrapper(S session, ServletContext servletContext) {
            super(session, servletContext);
        }

        public void invalidate() {
            super.invalidate();
            requestedSessionInvalidated = true;
            setCurrentSession(null);
            sessionRepository.delete(getId());
        }
    }
}
