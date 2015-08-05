/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2015 WEI CHEN LIN.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sora.util.akatsuki.compiler;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map.Entry;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;

/**
 * A file manager implementation that stores all output in memory.
 *
 * @author Gregory Kick
 */
// TODO(gak): under java 1.7 this could all be done with a PathFileManager
final class InMemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

	private final ClassLoader loader;

	private final LoadingCache<URI, JavaFileObject> inMemoryFileObjects = CacheBuilder.newBuilder()
			.build(new CacheLoader<URI, JavaFileObject>() {
				@Override
				public JavaFileObject load(URI key) {
					return new InMemoryJavaFileObject(key);
				}
			});

	InMemoryJavaFileManager(JavaFileManager fileManager, ClassLoader loader) {
		super(fileManager);
		this.loader = loader;
	}

	private static URI uriForFileObject(Location location, String packageName,
			String relativeName) {
		return URI.create("mem:///" + location.getName() + '/'
				+ CharMatcher.is('.').replaceFrom(packageName, '/') + '/' + relativeName);
	}

	private static URI uriForJavaFileObject(Location location, String className, Kind kind) {
		return URI.create("mem:///" + location.getName() + '/'
				+ CharMatcher.is('.').replaceFrom(className, '/') + kind.extension);
	}

	@Override
	public boolean isSameFile(FileObject a, FileObject b) {
		if (a instanceof InMemoryJavaFileObject) {
			if (b instanceof InMemoryJavaFileObject) {
				return ((InMemoryJavaFileObject) a).toUri()
						.equals(((InMemoryJavaFileObject) b).toUri());
			}
		}
		if (b instanceof InMemoryJavaFileObject) {
			return false;
		}
		return super.isSameFile(a, b);
	}

	@Override
	public FileObject getFileForInput(Location location, String packageName, String relativeName)
			throws IOException {
		if (location.isOutputLocation()) {
			return inMemoryFileObjects
					.getIfPresent(uriForFileObject(location, packageName, relativeName));
		} else {
			return super.getFileForInput(location, packageName, relativeName);
		}
	}

	@Override
	public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind)
			throws IOException {
		if (location.isOutputLocation()) {
			return inMemoryFileObjects
					.getIfPresent(uriForJavaFileObject(location, className, kind));
		} else {
			return super.getJavaFileForInput(location, className, kind);
		}
	}

	@Override
	public FileObject getFileForOutput(Location location, String packageName, String relativeName,
			FileObject sibling) throws IOException {
		URI uri = uriForFileObject(location, packageName, relativeName);
		return inMemoryFileObjects.getUnchecked(uri);
	}

	@Override
	public JavaFileObject getJavaFileForOutput(Location location, String className, final Kind kind,
			FileObject sibling) throws IOException {
		URI uri = uriForJavaFileObject(location, className, kind);
		return inMemoryFileObjects.getUnchecked(uri);
	}

	ImmutableList<JavaFileObject> getGeneratedSources() {
		ImmutableList.Builder<JavaFileObject> result = ImmutableList.builder();
		for (Entry<URI, JavaFileObject> entry : inMemoryFileObjects.asMap().entrySet()) {
			if (entry.getKey().getPath().startsWith("/" + StandardLocation.SOURCE_OUTPUT.name())
					&& (entry.getValue().getKind() == Kind.SOURCE)) {
				result.add(entry.getValue());
			}
		}
		return result.build();
	}

	ImmutableList<JavaFileObject> getOutputFiles() {
		return ImmutableList.copyOf(inMemoryFileObjects.asMap().values());
	}

	private JavaFileObject find(URI uri) {
		return inMemoryFileObjects.getIfPresent(uri);
	}

	@Override
	public ClassLoader getClassLoader(Location location) {
		return new DynamicClassLoader(loader, location);
	}

	private final class DynamicClassLoader extends ClassLoader {
		private final Location location;

		protected DynamicClassLoader(ClassLoader parent, Location location) {
			super(parent);
			this.location = location;
		}

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			return super.loadClass(name);
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {

			final URI uri = uriForJavaFileObject(location, name, Kind.CLASS);
			final InMemoryJavaFileObject object = (InMemoryJavaFileObject) InMemoryJavaFileManager.this.inMemoryFileObjects
					.getIfPresent(uri);
			if (object == null || object.lastModified == 0) {
				// file is does not exist or never written (empty)
				throw new ClassNotFoundException(name);
			}
			try {
				final byte[] read = object.data.get().read();
				return defineClass(name, read, 0, read.length);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	static final class InMemoryJavaFileObject extends SimpleJavaFileObject
			implements JavaFileObject {
		private long lastModified = 0L;
		private Optional<ByteSource> data = Optional.absent();

		InMemoryJavaFileObject(URI uri) {
			super(uri, deduceKind(uri));
		}

		static Kind deduceKind(URI uri) {
			String path = uri.getPath();
			for (Kind kind : Kind.values()) {
				if (path.endsWith(kind.extension)) {
					return kind;
				}
			}
			return Kind.OTHER;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			if (data.isPresent()) {
				return data.get().openStream();
			} else {
				throw new FileNotFoundException();
			}
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			return new ByteArrayOutputStream() {
				@Override
				public void close() throws IOException {
					super.close();
					data = Optional.of(ByteSource.wrap(toByteArray()));
					lastModified = System.currentTimeMillis();
				}
			};
		}

		@Override
		public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
			if (data.isPresent()) {
				return data.get().asCharSource(Charset.defaultCharset()).openStream();
			} else {
				throw new FileNotFoundException();
			}
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
			if (data.isPresent()) {
				return data.get().asCharSource(Charset.defaultCharset()).read();
			} else {
				throw new FileNotFoundException();
			}
		}

		@Override
		public Writer openWriter() throws IOException {
			return new StringWriter() {
				@Override
				public void close() throws IOException {
					super.close();
					data = Optional
							.of(ByteSource.wrap(toString().getBytes(Charset.defaultCharset())));
					lastModified = System.currentTimeMillis();
				}
			};
		}

		@Override
		public long getLastModified() {
			return lastModified;
		}

		@Override
		public boolean delete() {
			System.out.println("delete obj->" + this);
			this.data = Optional.absent();
			this.lastModified = 0L;
			return true;
		}

		@Override
		public String toString() {
			final ToStringHelper helper = MoreObjects.toStringHelper(this).add("uri", toUri())
					.add("kind", kind).add("lastModified", lastModified);
			return helper.toString();
		}

		public boolean isSource() {
			return kind.extension.equals(".java");
		}

		public void printSource(Writer writer) throws IOException {
			if (!isSource())
				throw new IllegalStateException(
						"cannot print file " + toUri() + ", not a source file");
			if (!data.isPresent())
				throw new IllegalStateException("file " + toUri() + " is not written yet");

			final ByteSource byteSource = data.orNull();
			if (byteSource == null)
				throw new IOException("unable to read file " + toUri() + ", stream not ready");

			final ImmutableList<String> lines = data.get().asCharSource(Charset.defaultCharset())
					.readLines();
			// get the number of digits
			String format = String.format("%%0%dd", String.valueOf(lines.size()).length());
			for (int i = 0; i < lines.size(); i++) {
				// TODO bad performance, use something better than String.format
				writer.append(String.format(format, i + 1)).append('.').append(lines.get(i));
				if (i != lines.size() - 1)
					writer.append("\n");
			}
		}

	}
}
