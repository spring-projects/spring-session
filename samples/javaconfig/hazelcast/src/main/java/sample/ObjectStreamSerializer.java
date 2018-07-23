/*
 * Copyright 2014-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

/**
 * A {@link StreamSerializer} that uses Java serialization to persist the session. This is
 * certainly not the most efficient way to persist sessions, but the example is intended
 * to demonstrate using minimal dependencies. For better serialization methods try using
 * <a href="https://github.com/EsotericSoftware/kryo">Kryo</a>.
 *
 * @author Rob Winch
 *
 */
public class ObjectStreamSerializer implements StreamSerializer<Object> {
	@Override
	public int getTypeId() {
		return 2;
	}

	@Override
	public void write(ObjectDataOutput objectDataOutput, Object object)
			throws IOException {
		ObjectOutputStream out = new ObjectOutputStream((OutputStream) objectDataOutput);
		out.writeObject(object);
		out.flush();
	}

	@Override
	public Object read(ObjectDataInput objectDataInput) throws IOException {
		ObjectInputStream in = new ObjectInputStream((InputStream) objectDataInput);
		try {
			return in.readObject();
		}
		catch (ClassNotFoundException ex) {
			throw new IOException(ex);
		}
	}

	@Override
	public void destroy() {
	}

}
