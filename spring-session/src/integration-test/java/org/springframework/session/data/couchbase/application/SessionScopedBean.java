package org.springframework.session.data.couchbase.application;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;

import static org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS;
import static org.springframework.web.context.request.RequestAttributes.REFERENCE_SESSION;

@Component
@Scope(value = REFERENCE_SESSION, proxyMode = TARGET_CLASS)
public class SessionScopedBean implements Serializable {

    private String text;
    private Integer number;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }
}
