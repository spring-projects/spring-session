package org.springframework.session.hazelcast;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.springframework.session.MapSession;

import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * A {@link com.hazelcast.nio.serialization.Serializer} implementation that
 * handles the (de)serialization of {@link MapSession} stored on {@link com.hazelcast.core.IMap}.
 *
 * The use of this serializer is optional and provides faster serialization of
 * sessions. If not configured to be used, Hazelcast will serialize sessions
 * via {@link java.io.Serializable} by default.
 *
 * If multiple instances of a Spring application is run, then all of them need to use
 * the same serialization method. If this serializer is registered on one instance
 * and not another one, then it will end up with HazelcastSerializationException.
 * The same applies when clients are configured to use this serializer but not the
 * members, and vice versa. Also note that, if a new instance is created with this
 * serialization but the existing Hazelcast cluster contains the values not serialized
 * by this but instead the default one, this will result in incompatibility again.
 *
 * <p>
 * An example of how to register the serializer on embedded instance can be seen below:
 *
 * <pre class="code">
 * Config config = new Config();
 *
 * // ... other configurations for Hazelcast ...
 *
 * SerializerConfig serializerConfig = new SerializerConfig();
 * serializerConfig.setImplementation(new HazelcastSessionSerializer()).setTypeClass(MapSession.class);
 * config.getSerializationConfig().addSerializerConfig(serializerConfig);
 *
 * HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
 * </pre>
 *
 * Below is the example of how to register the serializer on client instance. Note that,
 * to use the serializer in client/server mode, the serializer - and hence {@link MapSession},
 * must exist on the server's classpath and must be registered via {@link com.hazelcast.config.SerializerConfig}
 * with the configuration above for each server.
 *
 * <pre class="code">
 * ClientConfig clientConfig = new ClientConfig();
 *
 * // ... other configurations for Hazelcast Client ...
 *
 * SerializerConfig serializerConfig = new SerializerConfig();
 * serializerConfig.setImplementation(new HazelcastSessionSerializer()).setTypeClass(MapSession.class);
 * clientConfig.getSerializationConfig().addSerializerConfig(serializerConfig);
 *
 * HazelcastInstance hazelcastClient = HazelcastClient.newHazelcastClient(clientConfig);
 * </pre>
 *
 * @author Enes Ozcan
 */
public class HazelcastSessionSerializer implements StreamSerializer<MapSession> {

	private static final int SERIALIZER_TYPE_ID = 12345;

	@Override
	public void write(ObjectDataOutput out, MapSession session) throws IOException {
		out.writeUTF(session.getOriginalId());
		out.writeUTF(session.getId());
		writeInstant(out, session.getCreationTime());
		writeInstant(out, session.getLastAccessedTime());
		writeDuration(out, session.getMaxInactiveInterval());
		for (String attrName : session.getAttributeNames()) {
			Object attrValue = session.getAttribute(attrName);
			if (attrValue != null) {
				out.writeUTF(attrName);
				out.writeObject(attrValue);
			}
		}
	}

	private void writeInstant(ObjectDataOutput out, Instant instant) throws IOException {
		out.writeLong(instant.getEpochSecond());
		out.writeInt(instant.getNano());
	}

	private void writeDuration(ObjectDataOutput out, Duration duration) throws IOException {
		out.writeLong(duration.getSeconds());
		out.writeInt(duration.getNano());
	}

	@Override
	public MapSession read(ObjectDataInput in) throws IOException {
		String originalId = in.readUTF();
		MapSession cached = new MapSession(originalId);
		cached.setId(in.readUTF());
		cached.setCreationTime(readInstant(in));
		cached.setLastAccessedTime(readInstant(in));
		cached.setMaxInactiveInterval(readDuration(in));
		try {
			while (true) {
				// During write, it's not possible to write
				// number of non-null attributes without an extra
				// iteration. Hence the attributes are read until
				// EOF here.
				String attrName = in.readUTF();
				Object attrValue = in.readObject();
				cached.setAttribute(attrName, attrValue);
			}
		} catch (EOFException ignored) {
		}
		return cached;
	}

	private Instant readInstant(ObjectDataInput in) throws IOException {
		long seconds = in.readLong();
		int nanos = in.readInt();
		return Instant.ofEpochSecond(seconds, nanos);
	}

	private Duration readDuration(ObjectDataInput in) throws IOException {
		long seconds = in.readLong();
		int nanos = in.readInt();
		return Duration.ofSeconds(seconds, nanos);
	}

	@Override
	public int getTypeId() {
		return SERIALIZER_TYPE_ID;
	}

	@Override
	public void destroy() {
	}

}
