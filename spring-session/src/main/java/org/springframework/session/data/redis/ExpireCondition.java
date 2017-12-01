package org.springframework.session.data.redis;

/**
 * Defines a condition on whether the session should be updated or not.
 *
 * @author Chris Burrell
 */
public interface ExpireCondition {
    /**
     *
     * @return true to indicate the last access record on the session should be updated.
     */
    boolean shouldUpdateLastAccess();
}
