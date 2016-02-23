package org.springframework.session.data.mongo;

import com.mongodb.DBObject;
import org.junit.Test;
import org.springframework.session.ExpiringSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jakub Kubrynski
 */
public class StandardConversionTest {

	@Test
	public void verifyRoundTripSerialization() throws Exception {
		//given
		StandardMongoSessionToDBObjectConverter serializer = new StandardMongoSessionToDBObjectConverter();
		StandardDBObjectToMongoSessionConverter deserializer = new StandardDBObjectToMongoSessionConverter();
		MongoExpiringSession toSerialize = new MongoExpiringSession();
		toSerialize.setAttribute("username", "john_the_springer");

		//when
		DBObject dbObject = serializer.convert(toSerialize);
		ExpiringSession deserialized = deserializer.convert(dbObject);

		//then
		assertThat(deserialized).isEqualToComparingFieldByField(toSerialize);
	}
}
