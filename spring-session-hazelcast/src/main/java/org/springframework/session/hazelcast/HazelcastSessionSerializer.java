package org.springframework.session.hazelcast;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.springframework.session.MapSession;

import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

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
