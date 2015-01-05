package org.springframework.session;

/**
 * A {@link Session} that contains additional attributes that are useful for determining if a session is expired.
 *
 * @since 1.0
 * @author Rob Winch
 */
public interface ExpiringSession extends Session {

    /**
     * Gets the time when this session was created in milliseconds since midnight of 1/1/1970 GMT.
     *
     * @return the time when this session was created in milliseconds since midnight of 1/1/1970 GMT.
     */
    long getCreationTime();

    /**
     * Gets the last time this {@link Session} was accessed expressed in milliseconds since midnight of 1/1/1970 GMT
     *
     * @return the last time the client sent a request associated with the session expressed in milliseconds since midnight of 1/1/1970 GMT
     */
    long getLastAccessedTime();

    /**
     * Sets the maximum inactive interval in seconds between requests before this session will be invalidated. A negative time indicates that the session will never timeout.
     *
     * @param interval the number of seconds that the {@link Session} should be kept alive between client requests.
     */
    void setMaxInactiveIntervalInSeconds(int interval);

    /**
     * Gets the maximum inactive interval in seconds between requests before this session will be invalidated. A negative time indicates that the session will never timeout.
     *
     * @return the maximum inactive interval in seconds between requests before this session will be invalidated. A negative time indicates that the session will never timeout.
     */
    int getMaxInactiveIntervalInSeconds();

    /**
     * Returns true if the session is expired.
     *
     * @return true if the session is expired, else false.
     */
    boolean isExpired();

}
