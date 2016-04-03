package org.springframework.session.data.couchbase.application;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;

import java.io.Serializable;

@Component
@Scope(value = RequestAttributes.REFERENCE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionScopedBean implements Serializable {

    private String text;
    private Integer number;

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getNumber() {
        return this.number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }
}
