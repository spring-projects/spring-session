/*
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

class OnCommittedResponseWrapperTests {

	private static final String NL = "\r\n";

	@Mock
	HttpServletResponse delegate;

	@Mock
	PrintWriter writer;

	@Mock
	ServletOutputStream out;

	private OnCommittedResponseWrapper response;

	private boolean committed;

	@BeforeEach
	void setup() throws Exception {
		MockitoAnnotations.initMocks(this);
		this.response = new OnCommittedResponseWrapper(this.delegate) {
			@Override
			protected void onResponseCommitted() {
				OnCommittedResponseWrapperTests.this.committed = true;
			}
		};
		given(this.delegate.getWriter()).willReturn(this.writer);
		given(this.delegate.getOutputStream()).willReturn(this.out);
	}

	// --- printwriter

	@Test
	void printWriterHashCode() throws Exception {
		int expected = this.writer.hashCode();

		assertThat(this.response.getWriter().hashCode()).isEqualTo(expected);
	}

	@Test
	void printWriterCheckError() throws Exception {
		boolean expected = true;
		given(this.writer.checkError()).willReturn(expected);

		assertThat(this.response.getWriter().checkError()).isEqualTo(expected);
	}

	@Test
	void printWriterWriteInt() throws Exception {
		int expected = 1;

		this.response.getWriter().write(expected);

		verify(this.writer).write(expected);
	}

	@Test
	void printWriterWriteCharIntInt() throws Exception {
		char[] buff = new char[0];
		int off = 2;
		int len = 3;

		this.response.getWriter().write(buff, off, len);

		verify(this.writer).write(buff, off, len);
	}

	@Test
	void printWriterWriteChar() throws Exception {
		char[] buff = new char[0];

		this.response.getWriter().write(buff);

		verify(this.writer).write(buff);
	}

	@Test
	void printWriterWriteStringIntInt() throws Exception {
		String s = "";
		int off = 2;
		int len = 3;

		this.response.getWriter().write(s, off, len);

		verify(this.writer).write(s, off, len);
	}

	@Test
	void printWriterWriteString() throws Exception {
		String s = "";

		this.response.getWriter().write(s);

		verify(this.writer).write(s);
	}

	@Test
	void printWriterPrintBoolean() throws Exception {
		boolean b = true;

		this.response.getWriter().print(b);

		verify(this.writer).print(b);
	}

	@Test
	void printWriterPrintChar() throws Exception {
		char c = 1;

		this.response.getWriter().print(c);

		verify(this.writer).print(c);
	}

	@Test
	void printWriterPrintInt() throws Exception {
		int i = 1;

		this.response.getWriter().print(i);

		verify(this.writer).print(i);
	}

	@Test
	void printWriterPrintLong() throws Exception {
		long l = 1;

		this.response.getWriter().print(l);

		verify(this.writer).print(l);
	}

	@Test
	void printWriterPrintFloat() throws Exception {
		float f = 1;

		this.response.getWriter().print(f);

		verify(this.writer).print(f);
	}

	@Test
	void printWriterPrintDouble() throws Exception {
		double x = 1;

		this.response.getWriter().print(x);

		verify(this.writer).print(x);
	}

	@Test
	void printWriterPrintCharArray() throws Exception {
		char[] x = new char[0];

		this.response.getWriter().print(x);

		verify(this.writer).print(x);
	}

	@Test
	void printWriterPrintString() throws Exception {
		String x = "1";

		this.response.getWriter().print(x);

		verify(this.writer).print(x);
	}

	@Test
	void printWriterPrintObject() throws Exception {
		Object x = "1";

		this.response.getWriter().print(x);

		verify(this.writer).print(x);
	}

	@Test
	void printWriterPrintln() throws Exception {
		this.response.getWriter().println();

		verify(this.writer).println();
	}

	@Test
	void printWriterPrintlnBoolean() throws Exception {
		boolean b = true;

		this.response.getWriter().println(b);

		verify(this.writer).println(b);
	}

	@Test
	void printWriterPrintlnChar() throws Exception {
		char c = 1;

		this.response.getWriter().println(c);

		verify(this.writer).println(c);
	}

	@Test
	void printWriterPrintlnInt() throws Exception {
		int i = 1;

		this.response.getWriter().println(i);

		verify(this.writer).println(i);
	}

	@Test
	void printWriterPrintlnLong() throws Exception {
		long l = 1;

		this.response.getWriter().println(l);

		verify(this.writer).println(l);
	}

	@Test
	void printWriterPrintlnFloat() throws Exception {
		float f = 1;

		this.response.getWriter().println(f);

		verify(this.writer).println(f);
	}

	@Test
	void printWriterPrintlnDouble() throws Exception {
		double x = 1;

		this.response.getWriter().println(x);

		verify(this.writer).println(x);
	}

	@Test
	void printWriterPrintlnCharArray() throws Exception {
		char[] x = new char[0];

		this.response.getWriter().println(x);

		verify(this.writer).println(x);
	}

	@Test
	void printWriterPrintlnString() throws Exception {
		String x = "1";

		this.response.getWriter().println(x);

		verify(this.writer).println(x);
	}

	@Test
	void printWriterPrintlnObject() throws Exception {
		Object x = "1";

		this.response.getWriter().println(x);

		verify(this.writer).println(x);
	}

	@Test
	void printWriterPrintfStringObjectVargs() throws Exception {
		String format = "format";
		Object[] args = new Object[] { "1" };

		this.response.getWriter().printf(format, args);

		verify(this.writer).printf(format, args);
	}

	@Test
	void printWriterPrintfLocaleStringObjectVargs() throws Exception {
		Locale l = Locale.US;
		String format = "format";
		Object[] args = new Object[] { "1" };

		this.response.getWriter().printf(l, format, args);

		verify(this.writer).printf(l, format, args);
	}

	@Test
	void printWriterFormatStringObjectVargs() throws Exception {
		String format = "format";
		Object[] args = new Object[] { "1" };

		this.response.getWriter().format(format, args);

		verify(this.writer).format(format, args);
	}

	@Test
	void printWriterFormatLocaleStringObjectVargs() throws Exception {
		Locale l = Locale.US;
		String format = "format";
		Object[] args = new Object[] { "1" };

		this.response.getWriter().format(l, format, args);

		verify(this.writer).format(l, format, args);
	}

	@Test
	void printWriterAppendCharSequence() throws Exception {
		String x = "a";

		this.response.getWriter().append(x);

		verify(this.writer).append(x);
	}

	@Test
	void printWriterAppendCharSequenceIntInt() throws Exception {
		String x = "abcdef";
		int start = 1;
		int end = 3;

		this.response.getWriter().append(x, start, end);

		verify(this.writer).append(x, start, end);
	}

	@Test
	void printWriterAppendChar() throws Exception {
		char x = 1;

		this.response.getWriter().append(x);

		verify(this.writer).append(x);
	}

	// servletoutputstream

	@Test
	void outputStreamHashCode() throws Exception {
		int expected = this.out.hashCode();

		assertThat(this.response.getOutputStream().hashCode()).isEqualTo(expected);
	}

	@Test
	void outputStreamWriteInt() throws Exception {
		int expected = 1;

		this.response.getOutputStream().write(expected);

		verify(this.out).write(expected);
	}

	@Test
	void outputStreamWriteByte() throws Exception {
		byte[] expected = new byte[0];

		this.response.getOutputStream().write(expected);

		verify(this.out).write(expected);
	}

	@Test
	void outputStreamWriteByteIntInt() throws Exception {
		int start = 1;
		int end = 2;
		byte[] expected = new byte[0];

		this.response.getOutputStream().write(expected, start, end);

		verify(this.out).write(expected, start, end);
	}

	@Test
	void outputStreamPrintBoolean() throws Exception {
		boolean b = true;

		this.response.getOutputStream().print(b);

		verify(this.out).print(b);
	}

	@Test
	void outputStreamPrintChar() throws Exception {
		char c = 1;

		this.response.getOutputStream().print(c);

		verify(this.out).print(c);
	}

	@Test
	void outputStreamPrintInt() throws Exception {
		int i = 1;

		this.response.getOutputStream().print(i);

		verify(this.out).print(i);
	}

	@Test
	void outputStreamPrintLong() throws Exception {
		long l = 1;

		this.response.getOutputStream().print(l);

		verify(this.out).print(l);
	}

	@Test
	void outputStreamPrintFloat() throws Exception {
		float f = 1;

		this.response.getOutputStream().print(f);

		verify(this.out).print(f);
	}

	@Test
	void outputStreamPrintDouble() throws Exception {
		double x = 1;

		this.response.getOutputStream().print(x);

		verify(this.out).print(x);
	}

	@Test
	void outputStreamPrintString() throws Exception {
		String x = "1";

		this.response.getOutputStream().print(x);

		verify(this.out).print(x);
	}

	@Test
	void outputStreamPrintln() throws Exception {
		this.response.getOutputStream().println();

		verify(this.out).println();
	}

	@Test
	void outputStreamPrintlnBoolean() throws Exception {
		boolean b = true;

		this.response.getOutputStream().println(b);

		verify(this.out).println(b);
	}

	@Test
	void outputStreamPrintlnChar() throws Exception {
		char c = 1;

		this.response.getOutputStream().println(c);

		verify(this.out).println(c);
	}

	@Test
	void outputStreamPrintlnInt() throws Exception {
		int i = 1;

		this.response.getOutputStream().println(i);

		verify(this.out).println(i);
	}

	@Test
	void outputStreamPrintlnLong() throws Exception {
		long l = 1;

		this.response.getOutputStream().println(l);

		verify(this.out).println(l);
	}

	@Test
	void outputStreamPrintlnFloat() throws Exception {
		float f = 1;

		this.response.getOutputStream().println(f);

		verify(this.out).println(f);
	}

	@Test
	void outputStreamPrintlnDouble() throws Exception {
		double x = 1;

		this.response.getOutputStream().println(x);

		verify(this.out).println(x);
	}

	@Test
	void outputStreamPrintlnString() throws Exception {
		String x = "1";

		this.response.getOutputStream().println(x);

		verify(this.out).println(x);
	}

	// The amount of content specified in the setContentLength method of the response
	// has been greater than zero and has been written to the response.

	@Test
	void contentLengthPrintWriterWriteIntCommits() throws Exception {
		int expected = 1;
		this.response.setContentLength(String.valueOf(expected).length());

		this.response.getWriter().write(expected);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterWriteIntMultiDigitCommits() throws Exception {
		int expected = 10000;
		this.response.setContentLength(String.valueOf(expected).length());

		this.response.getWriter().write(expected);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPlus1PrintWriterWriteIntMultiDigitCommits() throws Exception {
		int expected = 10000;
		this.response.setContentLength(String.valueOf(expected).length() + 1);

		this.response.getWriter().write(expected);

		assertThat(this.committed).isFalse();

		this.response.getWriter().write(1);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterWriteCharIntIntCommits() throws Exception {
		char[] buff = new char[0];
		int off = 2;
		int len = 3;
		this.response.setContentLength(3);

		this.response.getWriter().write(buff, off, len);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterWriteCharCommits() throws Exception {
		char[] buff = new char[4];
		this.response.setContentLength(buff.length);

		this.response.getWriter().write(buff);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterWriteStringIntIntCommits() throws Exception {
		String s = "";
		int off = 2;
		int len = 3;
		this.response.setContentLength(3);

		this.response.getWriter().write(s, off, len);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterWriteStringCommits() throws IOException {
		String body = "something";
		this.response.setContentLength(body.length());

		this.response.getWriter().write(body);

		assertThat(this.committed).isTrue();
	}

	@Test
	void printWriterWriteStringContentLengthCommits() throws IOException {
		String body = "something";
		this.response.getWriter().write(body);

		this.response.setContentLength(body.length());

		assertThat(this.committed).isTrue();
	}

	@Test
	void printWriterWriteStringDoesNotCommit() throws IOException {
		String body = "something";

		this.response.getWriter().write(body);

		assertThat(this.committed).isFalse();
	}

	@Test
	void contentLengthPrintWriterPrintBooleanCommits() throws Exception {
		boolean b = true;
		this.response.setContentLength(1);

		this.response.getWriter().print(b);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintCharCommits() throws Exception {
		char c = 1;
		this.response.setContentLength(1);

		this.response.getWriter().print(c);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintIntCommits() throws Exception {
		int i = 1234;
		this.response.setContentLength(String.valueOf(i).length());

		this.response.getWriter().print(i);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintLongCommits() throws Exception {
		long l = 12345;
		this.response.setContentLength(String.valueOf(l).length());

		this.response.getWriter().print(l);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintFloatCommits() throws Exception {
		float f = 12345;
		this.response.setContentLength(String.valueOf(f).length());

		this.response.getWriter().print(f);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintDoubleCommits() throws Exception {
		double x = 1.2345;
		this.response.setContentLength(String.valueOf(x).length());

		this.response.getWriter().print(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintCharArrayCommits() throws Exception {
		char[] x = new char[10];
		this.response.setContentLength(x.length);

		this.response.getWriter().print(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintStringCommits() throws Exception {
		String x = "12345";
		this.response.setContentLength(x.length());

		this.response.getWriter().print(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintObjectCommits() throws Exception {
		Object x = "12345";
		this.response.setContentLength(String.valueOf(x).length());

		this.response.getWriter().print(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnCommits() throws Exception {
		this.response.setContentLength(NL.length());

		this.response.getWriter().println();

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnBooleanCommits() throws Exception {
		boolean b = true;
		this.response.setContentLength(1);

		this.response.getWriter().println(b);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnCharCommits() throws Exception {
		char c = 1;
		this.response.setContentLength(1);

		this.response.getWriter().println(c);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnIntCommits() throws Exception {
		int i = 12345;
		this.response.setContentLength(String.valueOf(i).length());

		this.response.getWriter().println(i);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnLongCommits() throws Exception {
		long l = 12345678;
		this.response.setContentLength(String.valueOf(l).length());

		this.response.getWriter().println(l);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnFloatCommits() throws Exception {
		float f = 1234;
		this.response.setContentLength(String.valueOf(f).length());

		this.response.getWriter().println(f);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnDoubleCommits() throws Exception {
		double x = 1;
		this.response.setContentLength(String.valueOf(x).length());

		this.response.getWriter().println(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnCharArrayCommits() throws Exception {
		char[] x = new char[20];
		this.response.setContentLength(x.length);

		this.response.getWriter().println(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnStringCommits() throws Exception {
		String x = "1";
		this.response.setContentLength(x.length());

		this.response.getWriter().println(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterPrintlnObjectCommits() throws Exception {
		Object x = "1";
		this.response.setContentLength(String.valueOf(x).length());

		this.response.getWriter().println(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterAppendCharSequenceCommits() throws Exception {
		String x = "a";
		this.response.setContentLength(x.length());

		this.response.getWriter().append(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterAppendCharSequenceIntIntCommits() throws Exception {
		String x = "abcdef";
		int start = 1;
		int end = 3;
		this.response.setContentLength(end - start);

		this.response.getWriter().append(x, start, end);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPrintWriterAppendCharCommits() throws Exception {
		char x = 1;
		this.response.setContentLength(1);

		this.response.getWriter().append(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamWriteIntCommits() throws Exception {
		int expected = 1;
		this.response.setContentLength(String.valueOf(expected).length());

		this.response.getOutputStream().write(expected);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamWriteIntMultiDigitCommits() throws Exception {
		int expected = 10000;
		this.response.setContentLength(String.valueOf(expected).length());

		this.response.getOutputStream().write(expected);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthPlus1OutputStreamWriteIntMultiDigitCommits() throws Exception {
		int expected = 10000;
		this.response.setContentLength(String.valueOf(expected).length() + 1);

		this.response.getOutputStream().write(expected);

		assertThat(this.committed).isFalse();

		this.response.getOutputStream().write(1);

		assertThat(this.committed).isTrue();
	}

	// gh-171
	@Test
	void contentLengthPlus1OutputStreamWriteByteArrayMultiDigitCommits() throws Exception {
		String expected = "{\n" + "  \"parameterName\" : \"_csrf\",\n"
				+ "  \"token\" : \"06300b65-c4aa-4c8f-8cda-39ee17f545a0\",\n" + "  \"headerName\" : \"X-CSRF-TOKEN\"\n"
				+ "}";
		this.response.setContentLength(expected.length() + 1);

		this.response.getOutputStream().write(expected.getBytes());

		assertThat(this.committed).isFalse();

		this.response.getOutputStream().write("1".getBytes(StandardCharsets.UTF_8));

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintBooleanCommits() throws Exception {
		boolean b = true;
		this.response.setContentLength(1);

		this.response.getOutputStream().print(b);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintCharCommits() throws Exception {
		char c = 1;
		this.response.setContentLength(1);

		this.response.getOutputStream().print(c);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintIntCommits() throws Exception {
		int i = 1234;
		this.response.setContentLength(String.valueOf(i).length());

		this.response.getOutputStream().print(i);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintLongCommits() throws Exception {
		long l = 12345;
		this.response.setContentLength(String.valueOf(l).length());

		this.response.getOutputStream().print(l);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintFloatCommits() throws Exception {
		float f = 12345;
		this.response.setContentLength(String.valueOf(f).length());

		this.response.getOutputStream().print(f);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintDoubleCommits() throws Exception {
		double x = 1.2345;
		this.response.setContentLength(String.valueOf(x).length());

		this.response.getOutputStream().print(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintStringCommits() throws Exception {
		String x = "12345";
		this.response.setContentLength(x.length());

		this.response.getOutputStream().print(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnCommits() throws Exception {
		this.response.setContentLength(NL.length());

		this.response.getOutputStream().println();

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnBooleanCommits() throws Exception {
		boolean b = true;
		this.response.setContentLength(1);

		this.response.getOutputStream().println(b);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnCharCommits() throws Exception {
		char c = 1;
		this.response.setContentLength(1);

		this.response.getOutputStream().println(c);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnIntCommits() throws Exception {
		int i = 12345;
		this.response.setContentLength(String.valueOf(i).length());

		this.response.getOutputStream().println(i);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnLongCommits() throws Exception {
		long l = 12345678;
		this.response.setContentLength(String.valueOf(l).length());

		this.response.getOutputStream().println(l);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnFloatCommits() throws Exception {
		float f = 1234;
		this.response.setContentLength(String.valueOf(f).length());

		this.response.getOutputStream().println(f);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnDoubleCommits() throws Exception {
		double x = 1;
		this.response.setContentLength(String.valueOf(x).length());

		this.response.getOutputStream().println(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthOutputStreamPrintlnStringCommits() throws Exception {
		String x = "1";
		this.response.setContentLength(x.length());

		this.response.getOutputStream().println(x);

		assertThat(this.committed).isTrue();
	}

	@Test
	void contentLengthDoesNotCommit() {
		String body = "something";

		this.response.setContentLength(body.length());

		assertThat(this.committed).isFalse();
	}

	@Test
	void contentLengthOutputStreamWriteStringCommits() throws IOException {
		String body = "something";
		this.response.setContentLength(body.length());

		this.response.getOutputStream().print(body);

		assertThat(this.committed).isTrue();
	}

	@Test
	void addHeaderContentLengthPrintWriterWriteStringCommits() throws Exception {
		int expected = 1234;
		this.response.addHeader("Content-Length", String.valueOf(String.valueOf(expected).length()));

		this.response.getWriter().write(expected);

		assertThat(this.committed).isTrue();
	}

	@Test
	void bufferSizePrintWriterWriteCommits() throws Exception {
		String expected = "1234567890";
		given(this.response.getBufferSize()).willReturn(expected.length());

		this.response.getWriter().write(expected);

		assertThat(this.committed).isTrue();
	}

	// gh-7261
	@Test
	void contentLengthLongOutputStreamWriteStringCommits() throws IOException {
		String body = "something";
		this.response.setContentLengthLong(body.length());

		this.response.getOutputStream().print(body);

		assertThat(this.committed).isTrue();
	}

	@Test
	void bufferSizeCommitsOnce() throws Exception {
		String expected = "1234567890";
		given(this.response.getBufferSize()).willReturn(expected.length());

		this.response.getWriter().write(expected);

		assertThat(this.committed).isTrue();

		this.committed = false;

		this.response.getWriter().write(expected);

		assertThat(this.committed).isFalse();
	}

}
