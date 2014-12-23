/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package sample.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.session.ExpiringSession;
import sample.data.ActiveWebSocketUserRepository;
import sample.websocket.WebSocketConnectHandler;
import sample.websocket.WebSocketDisconnectHandler;

/**
 * These handlers are separated from WebSocketConfig because they are specific to this application and do not demonstrate a typical Spring Session setup.
 *
 * @author Rob Winch
 */
@Configuration
public class WebSocketHandlersConfig<S extends ExpiringSession> {

    @Bean
    public WebSocketConnectHandler<S> webSocketConnectHandler(SimpMessageSendingOperations messagingTemplate, ActiveWebSocketUserRepository repository) {
        return new WebSocketConnectHandler<S>(messagingTemplate, repository);
    }

    @Bean
    public WebSocketDisconnectHandler<S> webSocketDisconnectHandler(SimpMessageSendingOperations messagingTemplate, ActiveWebSocketUserRepository repository) {
        return new WebSocketDisconnectHandler<S>(messagingTemplate, repository);
    }
}
