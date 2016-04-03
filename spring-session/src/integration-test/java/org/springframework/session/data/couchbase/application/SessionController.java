package org.springframework.session.data.couchbase.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.couchbase.CouchbaseSession;
import org.springframework.session.data.couchbase.CouchbaseSessionRepository;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("session")
public class SessionController {

    private static final String PRINCIPAL_NAME = "user";
    private static final String SESSION_ATTRIBUTE_NAME = "attribute";

    @Autowired(required = false)
    private SessionScopedBean sessionBean;
    @Autowired(required = false)
    private CouchbaseSessionRepository sessionRepository;

    @RequestMapping(value = "attribute", method = RequestMethod.POST)
    public void setAttribute(@RequestBody Message dto, HttpSession session) {
        session.setAttribute(SESSION_ATTRIBUTE_NAME, dto);
    }

    @RequestMapping(value = "attribute/global", method = RequestMethod.POST)
    public void setGlobalAttribute(@RequestBody Message dto, HttpSession session) {
        session.setAttribute(CouchbaseSession.globalAttributeName(SESSION_ATTRIBUTE_NAME), dto);
    }

    @RequestMapping("attribute")
    public Object getAttribute(HttpSession session) {
        return session.getAttribute(SESSION_ATTRIBUTE_NAME);
    }

    @RequestMapping("attribute/global")
    public Object getGlobalAttribute(HttpSession session) {
        return session.getAttribute(CouchbaseSession.globalAttributeName(SESSION_ATTRIBUTE_NAME));
    }

    @RequestMapping(value = "attribute", method = RequestMethod.DELETE)
    public void deleteAttribute(HttpSession session) {
        session.removeAttribute(SESSION_ATTRIBUTE_NAME);
    }

    @RequestMapping(value = "attribute/global", method = RequestMethod.DELETE)
    public void deleteGlobalAttribute(HttpSession session) {
        session.removeAttribute(CouchbaseSession.globalAttributeName(SESSION_ATTRIBUTE_NAME));
    }

    @RequestMapping(value = "bean", method = RequestMethod.POST)
    public void setBean(@RequestBody Message dto) {
        sessionBean.setText(dto.getText());
        sessionBean.setNumber(dto.getNumber());
    }

    @RequestMapping("bean")
    public Message getBean() {
        Message message = new Message();
        message.setText(sessionBean.getText());
        message.setNumber(sessionBean.getNumber());
        return message;
    }

    @RequestMapping(method = RequestMethod.DELETE)
    public void invalidateSession(HttpSession session) {
        session.invalidate();
    }

    @RequestMapping(value = "id", method = RequestMethod.PUT)
    public void changeSessionId(HttpServletRequest request) {
        request.changeSessionId();
    }

    @RequestMapping(value = "principal", method = RequestMethod.POST)
    public String setPrincipalAttribute(HttpSession session) {
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, PRINCIPAL_NAME);
        return session.getId();
    }

    @RequestMapping("principal")
    public Set<String> getPrincipalSessions() {
        Map<String, CouchbaseSession> sessionsById = sessionRepository.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, PRINCIPAL_NAME);
        return sessionsById.keySet();
    }

    @RequestMapping("attribute/names")
    public List<String> getAttributeNames(HttpSession session) {
        return Collections.list(session.getAttributeNames());
    }
}
