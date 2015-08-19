package sample;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.ExpiringSession;
import org.springframework.session.events.SessionDestroyedEvent;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryEvictedListener;

public class SessionEvictedListener implements EntryEvictedListener<String, ExpiringSession> {

    private ApplicationEventPublisher eventPublisher;
	
    public SessionEvictedListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
	
    public void entryEvicted(EntryEvent<String, ExpiringSession> event) {
        System.out.println("Session removed: " + event);
        eventPublisher.publishEvent(new SessionDestroyedEvent(this, event.getKey()));
    }

}
