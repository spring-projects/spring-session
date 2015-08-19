package sample;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.ExpiringSession;
import org.springframework.session.events.SessionCreatedEvent;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;

public class SessionCreatedListener implements EntryAddedListener<String, ExpiringSession> {

    private ApplicationEventPublisher eventPublisher;
	
    public SessionCreatedListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
	public void entryAdded(EntryEvent<String, ExpiringSession> event) {
		System.out.println("Session added: " + event);
        eventPublisher.publishEvent(new SessionCreatedEvent(this, event.getValue()));
	}
}
