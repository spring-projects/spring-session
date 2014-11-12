/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.session.web.socket.server;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.session.ExpiringSession;
import org.springframework.session.SessionRepository;

@RunWith(MockitoJUnitRunner.class)
public class SessionRepositoryMessageInterceptorTests {
    @Mock
    SessionRepository<ExpiringSession> sessionRepository;
    @Mock
    MessageChannel channel;
    @Mock
    ExpiringSession session;

    Message<?> createMessage;

    SimpMessageHeaderAccessor headers;

    SessionRepositoryMessageInterceptor<ExpiringSession> interceptor;

    @Before
    public void setup() {
        interceptor = new SessionRepositoryMessageInterceptor<ExpiringSession>(sessionRepository);
        headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId("session");
        headers.setSessionAttributes(new HashMap<String,Object>());
        setMessageType(SimpMessageType.MESSAGE);
        String sessionId = "http-session";
        setSessionId(sessionId);
        when(sessionRepository.getSession(sessionId)).thenReturn(session);
    }

    @Test(expected = IllegalArgumentException.class)
    public void preSendconstructorNullRepository() {
        new SessionRepositoryMessageInterceptor<ExpiringSession>(null);
    }

    @Test
    public void preSendNullMessage() {
        assertThat(interceptor.preSend(null, channel)).isNull();
    }

    @Test
    public void preSendConnectAckDoesNotInvokeSessionRepository() {
        setMessageType(SimpMessageType.CONNECT_ACK);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verifyZeroInteractions(sessionRepository);
    }

    @Test
    public void preSendHeartbeatDoesNotInvokeSessionRepository() {
        setMessageType(SimpMessageType.HEARTBEAT);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verifyZeroInteractions(sessionRepository);
    }

    @Test
    public void preSendDisconnectDoesNotInvokeSessionRepository() {
        setMessageType(SimpMessageType.DISCONNECT);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verifyZeroInteractions(sessionRepository);
    }

    @Test
    public void preSendOtherDoesNotInvokeSessionRepository() {
        setMessageType(SimpMessageType.OTHER);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verifyZeroInteractions(sessionRepository);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setMatchingMessageTypesNull() {
        interceptor.setMatchingMessageTypes(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setMatchingMessageTypesEmpty() {
        interceptor.setMatchingMessageTypes(Collections.<SimpMessageType>emptySet());
    }

    @Test
    public void preSendSetMatchingMessageTypes() {
        interceptor.setMatchingMessageTypes(EnumSet.of(SimpMessageType.DISCONNECT));
        setMessageType(SimpMessageType.DISCONNECT);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verify(sessionRepository).getSession(anyString());
        verify(sessionRepository).save(session);
    }

    @Test
    public void preSendConnectUpdatesLastUpdateTime() {
        setMessageType(SimpMessageType.CONNECT);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verify(sessionRepository).getSession(anyString());
        verify(sessionRepository).save(session);
    }

    @Test
    public void preSendMessageUpdatesLastUpdateTime() {
        setMessageType(SimpMessageType.MESSAGE);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verify(sessionRepository).getSession(anyString());
        verify(sessionRepository).save(session);
    }

    @Test
    public void preSendSubscribeUpdatesLastUpdateTime() {
        setMessageType(SimpMessageType.SUBSCRIBE);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verify(sessionRepository).getSession(anyString());
        verify(sessionRepository).save(session);
    }

    @Test
    public void preSendUnsubscribeUpdatesLastUpdateTime() {
        setMessageType(SimpMessageType.UNSUBSCRIBE);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verify(sessionRepository).getSession(anyString());
        verify(sessionRepository).save(session);
    }

    // This will updated when SPR-12288 is resolved
    @Test
    public void preSendExpiredSession() {
        setSessionId("expired");

        interceptor.preSend(createMessage(), channel);

        verify(sessionRepository,times(0)).save(any(ExpiringSession.class));
    }

    @Test
    public void preSendNullSessionId() {
        setSessionId(null);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verifyZeroInteractions(sessionRepository);
    }

    @Test
    public void preSendNullSessionAttributes() {
        headers.setSessionAttributes(null);

        assertThat(interceptor.preSend(createMessage(), channel)).isSameAs(createMessage);

        verifyZeroInteractions(sessionRepository);
    }

    @Test
    public void beforeHandshakeNotServletServerHttpRequest() throws Exception {
        assertThat(interceptor.beforeHandshake(null,null,null,null)).isTrue();

        verifyZeroInteractions(sessionRepository);
    }

    @Test
    public void beforeHandshakeNullSession() throws Exception {
        ServletServerHttpRequest request = new ServletServerHttpRequest(new MockHttpServletRequest());
        assertThat(interceptor.beforeHandshake(request,null,null,null)).isTrue();

        verifyZeroInteractions(sessionRepository);
    }

    @Test
    public void beforeHandshakeSession() throws Exception {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        HttpSession httpSession = httpRequest.getSession();
        ServletServerHttpRequest request = new ServletServerHttpRequest(httpRequest);
        Map<String,Object> attributes = new HashMap<String,Object>();

        assertThat(interceptor.beforeHandshake(request,null,null,attributes)).isTrue();

        assertThat(attributes.size()).isEqualTo(1);
        assertThat(SessionRepositoryMessageInterceptor.getSessionId(attributes)).isEqualTo(httpSession.getId());
    }

    /**
     * At the moment there is no need for afterHandshake to do anything.
     */
    @Test
    public void afterHandshakeDoesNothing() {
        interceptor.afterHandshake(null,null,null,null);

        verifyZeroInteractions(sessionRepository);
    }

    private void setSessionId(String id) {
        SessionRepositoryMessageInterceptor.setSessionId(headers.getSessionAttributes(), id);
    }

    private Message<?> createMessage() {
        createMessage = MessageBuilder.createMessage("", headers.getMessageHeaders());
        return createMessage;
    }

    private void setMessageType(SimpMessageType type) {
        headers.setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, type);
    }
}
