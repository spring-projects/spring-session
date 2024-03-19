package org.springframework.session;

/**
 * Defines a strategy to restore the principal in the session.
 *
 * @since 3.3
 * @author Marcus da Coregio
 */
public interface PersistentSessionPrincipalRestorer {

	/**
	 * Resolves the principal and store it inside the session
	 * @param principal the principal
	 * @param session the session to restore the principal into
	 * @throws PrincipalRestoreException if it was not possible to restore the principal
	 * into the session
	 */
	void restore(String principal, PersistentSessionRepository.PersistentSession session)
			throws PrincipalRestoreException;

}
