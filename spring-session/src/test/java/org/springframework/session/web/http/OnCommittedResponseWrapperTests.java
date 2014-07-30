package org.springframework.session.web.http;

import java.io.PrintWriter;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.session.web.http.OnCommittedResponseWrapper;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OnCommittedResponseWrapperTests {
    @Mock
    HttpServletResponse delegate;
    @Mock
    PrintWriter writer;
    @Mock
    ServletOutputStream out;

    HttpServletResponse response;

    boolean committed;

    @Before
    public void setup() throws Exception {
        response = new OnCommittedResponseWrapper(delegate) {
            @Override
            protected void onResponseCommitted() {
                committed = true;
            }
        };
        when(delegate.getWriter()).thenReturn(writer);
        when(delegate.getOutputStream()).thenReturn(out);
    }


    // --- printwriter

    @Test
    public void printWriterHashCode() throws Exception {
        int expected = writer.hashCode();

        assertThat(response.getWriter().hashCode()).isEqualTo(expected);
    }

    @Test
    public void printWriterCheckError() throws Exception {
        boolean expected = true;
        when(writer.checkError()).thenReturn(expected);

        assertThat(response.getWriter().checkError()).isEqualTo(expected);
    }

    @Test
    public void printWriterWriteInt() throws Exception {
        int expected = 1;

        response.getWriter().write(expected);

        verify(writer).write(expected);
    }

    @Test
    public void printWriterWriteCharIntInt() throws Exception {
        char[] buff = new char[0];
        int off = 2;
        int len = 3;

        response.getWriter().write(buff,off,len);

        verify(writer).write(buff,off,len);
    }

    @Test
    public void printWriterWriteChar() throws Exception {
        char[] buff = new char[0];

        response.getWriter().write(buff);

        verify(writer).write(buff);
    }

    @Test
    public void printWriterWriteStringIntInt() throws Exception {
        String s = "";
        int off = 2;
        int len = 3;

        response.getWriter().write(s,off,len);

        verify(writer).write(s,off,len);
    }

    @Test
    public void printWriterWriteString() throws Exception {
        String s = "";

        response.getWriter().write(s);

        verify(writer).write(s);
    }

    @Test
    public void printWriterPrintBoolean() throws Exception {
        boolean b = true;

        response.getWriter().print(b);

        verify(writer).print(b);
    }

    @Test
    public void printWriterPrintChar() throws Exception {
        char c = 1;

        response.getWriter().print(c);

        verify(writer).print(c);
    }

    @Test
    public void printWriterPrintInt() throws Exception {
        int i = 1;

        response.getWriter().print(i);

        verify(writer).print(i);
    }

    @Test
    public void printWriterPrintLong() throws Exception {
        long l = 1;

        response.getWriter().print(l);

        verify(writer).print(l);
    }

    @Test
    public void printWriterPrintFloat() throws Exception {
        float f = 1;

        response.getWriter().print(f);

        verify(writer).print(f);
    }

    @Test
    public void printWriterPrintDouble() throws Exception {
        double x = 1;

        response.getWriter().print(x);

        verify(writer).print(x);
    }

    @Test
    public void printWriterPrintCharArray() throws Exception {
        char[] x = new char[0];

        response.getWriter().print(x);

        verify(writer).print(x);
    }

    @Test
    public void printWriterPrintString() throws Exception {
        String x = "1";

        response.getWriter().print(x);

        verify(writer).print(x);
    }

    @Test
    public void printWriterPrintObject() throws Exception {
        Object x = "1";

        response.getWriter().print(x);

        verify(writer).print(x);
    }

    @Test
    public void printWriterPrintln() throws Exception {
        response.getWriter().println();

        verify(writer).println();
    }

    @Test
    public void printWriterPrintlnBoolean() throws Exception {
        boolean b = true;

        response.getWriter().println(b);

        verify(writer).println(b);
    }

    @Test
    public void printWriterPrintlnChar() throws Exception {
        char c = 1;

        response.getWriter().println(c);

        verify(writer).println(c);
    }

    @Test
    public void printWriterPrintlnInt() throws Exception {
        int i = 1;

        response.getWriter().println(i);

        verify(writer).println(i);
    }

    @Test
    public void printWriterPrintlnLong() throws Exception {
        long l = 1;

        response.getWriter().println(l);

        verify(writer).println(l);
    }

    @Test
    public void printWriterPrintlnFloat() throws Exception {
        float f = 1;

        response.getWriter().println(f);

        verify(writer).println(f);
    }

    @Test
    public void printWriterPrintlnDouble() throws Exception {
        double x = 1;

        response.getWriter().println(x);

        verify(writer).println(x);
    }

    @Test
    public void printWriterPrintlnCharArray() throws Exception {
        char[] x = new char[0];

        response.getWriter().println(x);

        verify(writer).println(x);
    }

    @Test
    public void printWriterPrintlnString() throws Exception {
        String x = "1";

        response.getWriter().println(x);

        verify(writer).println(x);
    }

    @Test
    public void printWriterPrintlnObject() throws Exception {
        Object x = "1";

        response.getWriter().println(x);

        verify(writer).println(x);
    }

    @Test
    public void printWriterPrintfStringObjectVargs() throws Exception {
        String format = "format";
        Object[] args = new Object[] { "1" };

        response.getWriter().printf(format, args);

        verify(writer).printf(format, args);
    }

    @Test
    public void printWriterPrintfLocaleStringObjectVargs() throws Exception {
        Locale l = Locale.US;
        String format = "format";
        Object[] args = new Object[] { "1" };

        response.getWriter().printf(l, format, args);

        verify(writer).printf(l, format, args);
    }

    @Test
    public void printWriterFormatStringObjectVargs() throws Exception {
        String format = "format";
        Object[] args = new Object[] { "1" };

        response.getWriter().format(format, args);

        verify(writer).format(format, args);
    }

    @Test
    public void printWriterFormatLocaleStringObjectVargs() throws Exception {
        Locale l = Locale.US;
        String format = "format";
        Object[] args = new Object[] { "1" };

        response.getWriter().format(l, format, args);

        verify(writer).format(l, format, args);
    }


    @Test
    public void printWriterAppendCharSequence() throws Exception {
        String x = "a";

        response.getWriter().append(x);

        verify(writer).append(x);
    }

    @Test
    public void printWriterAppendCharSequenceIntInt() throws Exception {
        String x = "abcdef";
        int start = 1;
        int end = 3;

        response.getWriter().append(x, start, end);

        verify(writer).append(x, start, end);
    }


    @Test
    public void printWriterAppendChar() throws Exception {
        char x = 1;

        response.getWriter().append(x);

        verify(writer).append(x);
    }

    // servletoutputstream


    @Test
    public void outputStreamHashCode() throws Exception {
        int expected = out.hashCode();

        assertThat(response.getOutputStream().hashCode()).isEqualTo(expected);
    }

    @Test
    public void outputStreamWriteInt() throws Exception {
        int expected = 1;

        response.getOutputStream().write(expected);

        verify(out).write(expected);
    }

    @Test
    public void outputStreamWriteByte() throws Exception {
        byte[] expected = new byte[0];

        response.getOutputStream().write(expected);

        verify(out).write(expected);
    }

    @Test
    public void outputStreamWriteByteIntInt() throws Exception {
        int start = 1;
        int end = 2;
        byte[] expected = new byte[0];

        response.getOutputStream().write(expected, start, end);

        verify(out).write(expected, start, end);
    }

    @Test
    public void outputStreamPrintBoolean() throws Exception {
        boolean b = true;

        response.getOutputStream().print(b);

        verify(out).print(b);
    }

    @Test
    public void outputStreamPrintChar() throws Exception {
        char c = 1;

        response.getOutputStream().print(c);

        verify(out).print(c);
    }

    @Test
    public void outputStreamPrintInt() throws Exception {
        int i = 1;

        response.getOutputStream().print(i);

        verify(out).print(i);
    }

    @Test
    public void outputStreamPrintLong() throws Exception {
        long l = 1;

        response.getOutputStream().print(l);

        verify(out).print(l);
    }

    @Test
    public void outputStreamPrintFloat() throws Exception {
        float f = 1;

        response.getOutputStream().print(f);

        verify(out).print(f);
    }

    @Test
    public void outputStreamPrintDouble() throws Exception {
        double x = 1;

        response.getOutputStream().print(x);

        verify(out).print(x);
    }

    @Test
    public void outputStreamPrintString() throws Exception {
        String x = "1";

        response.getOutputStream().print(x);

        verify(out).print(x);
    }

    @Test
    public void outputStreamPrintln() throws Exception {
        response.getOutputStream().println();

        verify(out).println();
    }

    @Test
    public void outputStreamPrintlnBoolean() throws Exception {
        boolean b = true;

        response.getOutputStream().println(b);

        verify(out).println(b);
    }

    @Test
    public void outputStreamPrintlnChar() throws Exception {
        char c = 1;

        response.getOutputStream().println(c);

        verify(out).println(c);
    }

    @Test
    public void outputStreamPrintlnInt() throws Exception {
        int i = 1;

        response.getOutputStream().println(i);

        verify(out).println(i);
    }

    @Test
    public void outputStreamPrintlnLong() throws Exception {
        long l = 1;

        response.getOutputStream().println(l);

        verify(out).println(l);
    }

    @Test
    public void outputStreamPrintlnFloat() throws Exception {
        float f = 1;

        response.getOutputStream().println(f);

        verify(out).println(f);
    }

    @Test
    public void outputStreamPrintlnDouble() throws Exception {
        double x = 1;

        response.getOutputStream().println(x);

        verify(out).println(x);
    }

    @Test
    public void outputStreamPrintlnString() throws Exception {
        String x = "1";

        response.getOutputStream().println(x);

        verify(out).println(x);
    }
}