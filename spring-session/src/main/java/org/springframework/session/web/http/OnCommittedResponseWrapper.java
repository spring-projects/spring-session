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

package org.springframework.session.web.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for response wrappers which encapsulate the logic for handling an event when
 * the {@link javax.servlet.http.HttpServletResponse} is committed.
 *
 * @author Rob Winch
 * @since 1.0
 */
abstract class OnCommittedResponseWrapper extends HttpServletResponseWrapper {
	private final Log logger = LogFactory.getLog(getClass());

	private boolean disableOnCommitted;

	/**
	 * The Content-Length response header. If this is greater than 0, then once
	 * {@link #contentWritten} is larger than or equal the response is considered
	 * committed.
	 */
	private long contentLength;

	/**
	 * The size of data written to the response body.
	 */
	private long contentWritten;

	/**
	 * Create a new {@link OnCommittedResponseWrapper}.
	 * @param response the response to be wrapped
	 */
	OnCommittedResponseWrapper(HttpServletResponse response) {
		super(response);
	}

	@Override
	public void addHeader(String name, String value) {
		if ("Content-Length".equalsIgnoreCase(name)) {
			setContentLength(Long.parseLong(value));
		}
		super.addHeader(name, value);
	}

	@Override
	public void setContentLength(int len) {
		setContentLength((long) len);
		super.setContentLength(len);
	}

	private void setContentLength(long len) {
		this.contentLength = len;
		checkContentLength(0);
	}

	/**
	 * Invoke this method to disable invoking
	 * {@link OnCommittedResponseWrapper#onResponseCommitted()} when the
	 * {@link javax.servlet.http.HttpServletResponse} is committed. This can be useful in
	 * the event that Async Web Requests are made.
	 */
	public void disableOnResponseCommitted() {
		this.disableOnCommitted = true;
	}

	/**
	 * Implement the logic for handling the {@link javax.servlet.http.HttpServletResponse}
	 * being committed.
	 */
	protected abstract void onResponseCommitted();

	/**
	 * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked
	 * before calling the superclass <code>sendError()</code>.
	 * @param sc the error status code
	 */
	@Override
	public final void sendError(int sc) throws IOException {
		doOnResponseCommitted();
		super.sendError(sc);
	}

	/**
	 * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked
	 * before calling the superclass <code>sendError()</code>.
	 * @param sc the error status code
	 */
	@Override
	public final void sendError(int sc, String msg) throws IOException {
		doOnResponseCommitted();
		super.sendError(sc, msg);
	}

	/**
	 * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked
	 * before calling the superclass <code>sendRedirect()</code>.
	 * @param location the redirect URL location
	 */
	@Override
	public final void sendRedirect(String location) throws IOException {
		doOnResponseCommitted();
		super.sendRedirect(location);
	}

	/**
	 * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked
	 * before calling the calling <code>getOutputStream().close()</code> or
	 * <code>getOutputStream().flush()</code>.
	 * @throws IOException if an input or output exception occurred
	 */
	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return new SaveContextServletOutputStream(super.getOutputStream());
	}

	/**
	 * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked
	 * before calling the <code>getWriter().close()</code> or
	 * <code>getWriter().flush()</code>.
	 * @throws IOException if an input or output exception occurred
	 */
	@Override
	public PrintWriter getWriter() throws IOException {
		return new SaveContextPrintWriter(super.getWriter());
	}

	/**
	 * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked
	 * before calling the superclass <code>flushBuffer()</code>.
	 * @throws IOException if an input or output exception occurred
	 */
	@Override
	public void flushBuffer() throws IOException {
		doOnResponseCommitted();
		super.flushBuffer();
	}

	private void trackContentLength(boolean content) {
		checkContentLength(content ? 4 : 5); // TODO Localization
	}

	private void trackContentLength(char content) {
		checkContentLength(1);
	}

	private void trackContentLength(Object content) {
		trackContentLength(String.valueOf(content));
	}

	private void trackContentLength(byte[] content) {
		checkContentLength(content == null ? 0 : content.length);
	}

	private void trackContentLength(char[] content) {
		checkContentLength(content == null ? 0 : content.length);
	}

	private void trackContentLength(int content) {
		trackContentLength(String.valueOf(content));
	}

	private void trackContentLength(float content) {
		trackContentLength(String.valueOf(content));
	}

	private void trackContentLength(double content) {
		trackContentLength(String.valueOf(content));
	}

	private void trackContentLengthLn() {
		trackContentLength("\r\n");
	}

	private void trackContentLength(String content) {
		checkContentLength(content.length());
	}

	/**
	 * Adds the contentLengthToWrite to the total contentWritten size and checks to see if
	 * the response should be written.
	 *
	 * @param contentLengthToWrite the size of the content that is about to be written.
	 */
	private void checkContentLength(long contentLengthToWrite) {
		this.contentWritten += contentLengthToWrite;
		boolean isBodyFullyWritten = this.contentLength > 0
				&& this.contentWritten >= this.contentLength;
		int bufferSize = getBufferSize();
		boolean requiresFlush = bufferSize > 0 && this.contentWritten >= bufferSize;
		if (isBodyFullyWritten || requiresFlush) {
			doOnResponseCommitted();
		}
	}

	/**
	 * Calls <code>onResponseCommmitted()</code> with the current contents as long as
	 * {@link #disableOnResponseCommitted()} was not invoked.
	 */
	private void doOnResponseCommitted() {
		if (!this.disableOnCommitted) {
			onResponseCommitted();
			disableOnResponseCommitted();
		}
		else if (this.logger.isDebugEnabled()) {
			this.logger.debug("Skip invoking on");
		}
	}

	/**
	 * Ensures {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before
	 * calling the prior to methods that commit the response. We delegate all methods to
	 * the original {@link java.io.PrintWriter} to ensure that the behavior is as close to
	 * the original {@link java.io.PrintWriter} as possible. See SEC-2039
	 * @author Rob Winch
	 */
	private class SaveContextPrintWriter extends PrintWriter {
		private final PrintWriter delegate;

		SaveContextPrintWriter(PrintWriter delegate) {
			super(delegate);
			this.delegate = delegate;
		}

		public void flush() {
			doOnResponseCommitted();
			this.delegate.flush();
		}

		public void close() {
			doOnResponseCommitted();
			this.delegate.close();
		}

		public int hashCode() {
			return this.delegate.hashCode();
		}

		public boolean equals(Object obj) {
			return this.delegate.equals(obj);
		}

		public String toString() {
			return getClass().getName() + "[delegate=" + this.delegate.toString() + "]";
		}

		public boolean checkError() {
			return this.delegate.checkError();
		}

		public void write(int c) {
			trackContentLength(c);
			this.delegate.write(c);
		}

		public void write(char[] buf, int off, int len) {
			checkContentLength(len);
			this.delegate.write(buf, off, len);
		}

		public void write(char[] buf) {
			trackContentLength(buf);
			this.delegate.write(buf);
		}

		public void write(String s, int off, int len) {
			checkContentLength(len);
			this.delegate.write(s, off, len);
		}

		public void write(String s) {
			trackContentLength(s);
			this.delegate.write(s);
		}

		public void print(boolean b) {
			trackContentLength(b);
			this.delegate.print(b);
		}

		public void print(char c) {
			trackContentLength(c);
			this.delegate.print(c);
		}

		public void print(int i) {
			trackContentLength(i);
			this.delegate.print(i);
		}

		public void print(long l) {
			trackContentLength(l);
			this.delegate.print(l);
		}

		public void print(float f) {
			trackContentLength(f);
			this.delegate.print(f);
		}

		public void print(double d) {
			trackContentLength(d);
			this.delegate.print(d);
		}

		public void print(char[] s) {
			trackContentLength(s);
			this.delegate.print(s);
		}

		public void print(String s) {
			trackContentLength(s);
			this.delegate.print(s);
		}

		public void print(Object obj) {
			trackContentLength(obj);
			this.delegate.print(obj);
		}

		public void println() {
			trackContentLengthLn();
			this.delegate.println();
		}

		public void println(boolean x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(char x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(int x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(long x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(float x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(double x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(char[] x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(String x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public void println(Object x) {
			trackContentLength(x);
			trackContentLengthLn();
			this.delegate.println(x);
		}

		public PrintWriter printf(String format, Object... args) {
			return this.delegate.printf(format, args);
		}

		public PrintWriter printf(Locale l, String format, Object... args) {
			return this.delegate.printf(l, format, args);
		}

		public PrintWriter format(String format, Object... args) {
			return this.delegate.format(format, args);
		}

		public PrintWriter format(Locale l, String format, Object... args) {
			return this.delegate.format(l, format, args);
		}

		public PrintWriter append(CharSequence csq) {
			checkContentLength(csq.length());
			return this.delegate.append(csq);
		}

		public PrintWriter append(CharSequence csq, int start, int end) {
			checkContentLength(end - start);
			return this.delegate.append(csq, start, end);
		}

		public PrintWriter append(char c) {
			trackContentLength(c);
			return this.delegate.append(c);
		}
	}

	/**
	 * Ensures{@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before
	 * calling methods that commit the response. We delegate all methods to the original
	 * {@link javax.servlet.ServletOutputStream} to ensure that the behavior is as close
	 * to the original {@link javax.servlet.ServletOutputStream} as possible. See SEC-2039
	 *
	 * @author Rob Winch
	 */
	private class SaveContextServletOutputStream extends ServletOutputStream {
		private final ServletOutputStream delegate;

		SaveContextServletOutputStream(ServletOutputStream delegate) {
			this.delegate = delegate;
		}

		public void write(int b) throws IOException {
			trackContentLength(b);
			this.delegate.write(b);
		}

		public void flush() throws IOException {
			doOnResponseCommitted();
			this.delegate.flush();
		}

		public void close() throws IOException {
			doOnResponseCommitted();
			this.delegate.close();
		}

		public int hashCode() {
			return this.delegate.hashCode();
		}

		public boolean equals(Object obj) {
			return this.delegate.equals(obj);
		}

		public void print(boolean b) throws IOException {
			trackContentLength(b);
			this.delegate.print(b);
		}

		public void print(char c) throws IOException {
			trackContentLength(c);
			this.delegate.print(c);
		}

		public void print(double d) throws IOException {
			trackContentLength(d);
			this.delegate.print(d);
		}

		public void print(float f) throws IOException {
			trackContentLength(f);
			this.delegate.print(f);
		}

		public void print(int i) throws IOException {
			trackContentLength(i);
			this.delegate.print(i);
		}

		public void print(long l) throws IOException {
			trackContentLength(l);
			this.delegate.print(l);
		}

		public void print(String s) throws IOException {
			trackContentLength(s);
			this.delegate.print(s);
		}

		public void println() throws IOException {
			trackContentLengthLn();
			this.delegate.println();
		}

		public void println(boolean b) throws IOException {
			trackContentLength(b);
			trackContentLengthLn();
			this.delegate.println(b);
		}

		public void println(char c) throws IOException {
			trackContentLength(c);
			trackContentLengthLn();
			this.delegate.println(c);
		}

		public void println(double d) throws IOException {
			trackContentLength(d);
			trackContentLengthLn();
			this.delegate.println(d);
		}

		public void println(float f) throws IOException {
			trackContentLength(f);
			trackContentLengthLn();
			this.delegate.println(f);
		}

		public void println(int i) throws IOException {
			trackContentLength(i);
			trackContentLengthLn();
			this.delegate.println(i);
		}

		public void println(long l) throws IOException {
			trackContentLength(l);
			trackContentLengthLn();
			this.delegate.println(l);
		}

		public void println(String s) throws IOException {
			trackContentLength(s);
			trackContentLengthLn();
			this.delegate.println(s);
		}

		public void write(byte[] b) throws IOException {
			trackContentLength(b);
			this.delegate.write(b);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			checkContentLength(len);
			this.delegate.write(b, off, len);
		}

		public String toString() {
			return getClass().getName() + "[delegate=" + this.delegate.toString() + "]";
		}
	}
}
