/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.session.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Base class for response wrappers which encapsulate the logic for handling an event when the
 * {@link javax.servlet.http.HttpServletResponse} is committed.
 *
 * @author Rob Winch
 */
abstract class OnCommittedResponseWrapper extends HttpServletResponseWrapper {
    private final Log logger = LogFactory.getLog(getClass());

    private boolean disableOnCommitted;

    /**
     * @param response the response to be wrapped
     */
    public OnCommittedResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    /**
     * Invoke this method to disable invoking {@link OnCommittedResponseWrapper#onResponseCommitted()} when the {@link javax.servlet.http.HttpServletResponse} is
     * committed. This can be useful in the event that Async Web Requests are
     * made.
     */
    public void disableOnResponseCommitted() {
        this.disableOnCommitted = true;
    }

    /**
     * Implement the logic for handling the {@link javax.servlet.http.HttpServletResponse} being committed
     */
    protected abstract void onResponseCommitted();

    /**
     * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling the
     * superclass <code>sendError()</code>
     */
    @Override
    public final void sendError(int sc) throws IOException {
        doOnResponseCommitted();
        super.sendError(sc);
    }

    /**
     * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling the
     * superclass <code>sendError()</code>
     */
    @Override
    public final void sendError(int sc, String msg) throws IOException {
        doOnResponseCommitted();
        super.sendError(sc, msg);
    }

    /**
     * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling the
     * superclass <code>sendRedirect()</code>
     */
    @Override
    public final void sendRedirect(String location) throws IOException {
        doOnResponseCommitted();
        super.sendRedirect(location);
    }

    /**
     * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling the calling
     * <code>getOutputStream().close()</code> or <code>getOutputStream().flush()</code>
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return new SaveContextServletOutputStream(super.getOutputStream());
    }

    /**
     * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling the
     * <code>getWriter().close()</code> or <code>getWriter().flush()</code>
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        return new SaveContextPrintWriter(super.getWriter());
    }

    /**
     * Makes sure {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling the
     * superclass <code>flushBuffer()</code>
     */
    @Override
    public void flushBuffer() throws IOException {
        doOnResponseCommitted();
        super.flushBuffer();
    }

    /**
     * Calls <code>onResponseCommmitted()</code> with the current contents as long as
     * {@link #disableOnResponseCommitted()()} was not invoked.
     */
    private void doOnResponseCommitted() {
        if(!disableOnCommitted) {
            onResponseCommitted();
        } else if(logger.isDebugEnabled()){
            logger.debug("Skip invoking on");
        }
    }

    /**
     * Ensures {@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling the prior to methods that commit the response. We delegate all methods
     * to the original {@link java.io.PrintWriter} to ensure that the behavior is as close to the original {@link java.io.PrintWriter}
     * as possible. See SEC-2039
     * @author Rob Winch
     */
    private class SaveContextPrintWriter extends PrintWriter {
        private final PrintWriter delegate;

        public SaveContextPrintWriter(PrintWriter delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        public void flush() {
            doOnResponseCommitted();
            delegate.flush();
        }

        public void close() {
            doOnResponseCommitted();
            delegate.close();
        }

        public int hashCode() {
            return delegate.hashCode();
        }

        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        public String toString() {
            return getClass().getName() + "[delegate=" + delegate.toString() + "]";
        }

        public boolean checkError() {
            return delegate.checkError();
        }

        public void write(int c) {
            delegate.write(c);
        }

        public void write(char[] buf, int off, int len) {
            delegate.write(buf, off, len);
        }

        public void write(char[] buf) {
            delegate.write(buf);
        }

        public void write(String s, int off, int len) {
            delegate.write(s, off, len);
        }

        public void write(String s) {
            delegate.write(s);
        }

        public void print(boolean b) {
            delegate.print(b);
        }

        public void print(char c) {
            delegate.print(c);
        }

        public void print(int i) {
            delegate.print(i);
        }

        public void print(long l) {
            delegate.print(l);
        }

        public void print(float f) {
            delegate.print(f);
        }

        public void print(double d) {
            delegate.print(d);
        }

        public void print(char[] s) {
            delegate.print(s);
        }

        public void print(String s) {
            delegate.print(s);
        }

        public void print(Object obj) {
            delegate.print(obj);
        }

        public void println() {
            delegate.println();
        }

        public void println(boolean x) {
            delegate.println(x);
        }

        public void println(char x) {
            delegate.println(x);
        }

        public void println(int x) {
            delegate.println(x);
        }

        public void println(long x) {
            delegate.println(x);
        }

        public void println(float x) {
            delegate.println(x);
        }

        public void println(double x) {
            delegate.println(x);
        }

        public void println(char[] x) {
            delegate.println(x);
        }

        public void println(String x) {
            delegate.println(x);
        }

        public void println(Object x) {
            delegate.println(x);
        }

        public PrintWriter printf(String format, Object... args) {
            return delegate.printf(format, args);
        }

        public PrintWriter printf(Locale l, String format, Object... args) {
            return delegate.printf(l, format, args);
        }

        public PrintWriter format(String format, Object... args) {
            return delegate.format(format, args);
        }

        public PrintWriter format(Locale l, String format, Object... args) {
            return delegate.format(l, format, args);
        }

        public PrintWriter append(CharSequence csq) {
            return delegate.append(csq);
        }

        public PrintWriter append(CharSequence csq, int start, int end) {
            return delegate.append(csq, start, end);
        }

        public PrintWriter append(char c) {
            return delegate.append(c);
        }
    }

    /**
     * Ensures{@link OnCommittedResponseWrapper#onResponseCommitted()} is invoked before calling methods that commit the response. We delegate all methods
     * to the original {@link javax.servlet.ServletOutputStream} to ensure that the behavior is as close to the original {@link javax.servlet.ServletOutputStream}
     * as possible. See SEC-2039
     *
     * @author Rob Winch
     */
    private class SaveContextServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;

        public SaveContextServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        public void write(int b) throws IOException {
            this.delegate.write(b);
        }

        public void flush() throws IOException {
            doOnResponseCommitted();
            delegate.flush();
        }

        public void close() throws IOException {
            doOnResponseCommitted();
            delegate.close();
        }

        public int hashCode() {
            return delegate.hashCode();
        }

        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        public void print(boolean b) throws IOException {
            delegate.print(b);
        }

        public void print(char c) throws IOException {
            delegate.print(c);
        }

        public void print(double d) throws IOException {
            delegate.print(d);
        }

        public void print(float f) throws IOException {
            delegate.print(f);
        }

        public void print(int i) throws IOException {
            delegate.print(i);
        }

        public void print(long l) throws IOException {
            delegate.print(l);
        }

        public void print(String arg0) throws IOException {
            delegate.print(arg0);
        }

        public void println() throws IOException {
            delegate.println();
        }

        public void println(boolean b) throws IOException {
            delegate.println(b);
        }

        public void println(char c) throws IOException {
            delegate.println(c);
        }

        public void println(double d) throws IOException {
            delegate.println(d);
        }

        public void println(float f) throws IOException {
            delegate.println(f);
        }

        public void println(int i) throws IOException {
            delegate.println(i);
        }

        public void println(long l) throws IOException {
            delegate.println(l);
        }

        public void println(String s) throws IOException {
            delegate.println(s);
        }

        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        public String toString() {
            return getClass().getName() + "[delegate=" + delegate.toString() + "]";
        }
    }
}