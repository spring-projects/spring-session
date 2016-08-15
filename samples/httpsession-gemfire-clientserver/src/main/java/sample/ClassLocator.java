/*
 * Copyright 2014-2016 the original author or authors.
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

/**
 * The ClassLocator class...
 *
 * @author John Blum
 * @since 1.0.0
 */
public final class ClassLocator {

	private ClassLocator() {
	}

	public static void main(final String[] args) throws ClassNotFoundException {
		String className = "org.w3c.dom.ElementTraversal";
		//String className = (args.length > 0 ? args[0] : "com.gemstone.gemfire.cache.Cache");
		Class<?> type = Class.forName(className);
		String resourceName = type.getName().replaceAll("\\.", "/").concat(".class");
		System.out.printf("class [%1$s] with resource name [%2$s] is found in [%3$s]%n",
			className, resourceName, type.getClassLoader().getResource(resourceName));
	}
}
