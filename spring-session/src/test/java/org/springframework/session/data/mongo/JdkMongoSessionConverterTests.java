package org.springframework.session.data.mongo;

import com.mongodb.DBObject;
import org.junit.Test;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jakub Kubrynski
 */
public class JdkMongoSessionConverterTests {

	JdkMongoSessionConverter sut = new JdkMongoSessionConverter();

	@Test
	public void verifyRoundTripSerialization() throws Exception {
		//given
		MongoExpiringSession toSerialize = new MongoExpiringSession();
		toSerialize.setAttribute("username", "john_the_springer");

		//when
		DBObject dbObject = convertToDBObject(toSerialize);
		ExpiringSession deserialized = convertToSession(dbObject);

		//then
		assertThat(deserialized).isEqualToComparingFieldByField(toSerialize);
	}

	@Test
	public void shouldExtractPrincipalNameFromAttributes() throws Exception {
		//given
		MongoExpiringSession toSerialize = new MongoExpiringSession();
		String principalName = "john_the_springer";
		toSerialize.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);

		//when
		DBObject dbObject = convertToDBObject(toSerialize);

		//then
		assertThat(dbObject.get("principal")).isEqualTo(principalName);
	}

	@Test
	public void shouldExtractPrincipalNameFromAuthentication() throws Exception {
		//given
		MongoExpiringSession toSerialize = new MongoExpiringSession();
		String principalName = "john_the_springer";
		SecurityContextImpl context = new SecurityContextImpl();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(principalName, null));
		toSerialize.setAttribute("SPRING_SECURITY_CONTEXT", context);

		//when
		DBObject dbObject = convertToDBObject(toSerialize);

		//then
		assertThat(dbObject.get("principal")).isEqualTo(principalName);
	}

	MongoExpiringSession convertToSession(DBObject session) {
		return (MongoExpiringSession) sut.convert(session, TypeDescriptor.valueOf(DBObject.class), TypeDescriptor.valueOf(MongoExpiringSession.class));
	}

	DBObject convertToDBObject(MongoExpiringSession session) {
		return (DBObject) sut.convert(session, TypeDescriptor.valueOf(MongoExpiringSession.class), TypeDescriptor.valueOf(DBObject.class));
	}
}
