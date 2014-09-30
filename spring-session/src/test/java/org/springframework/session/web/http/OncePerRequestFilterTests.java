package org.springframework.session.web.http;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.*;

public class OncePerRequestFilterTests {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;
    private OncePerRequestFilter filter;
    private HttpServlet servlet;


    private List<OncePerRequestFilter> invocations;

    @Before
    @SuppressWarnings("serial")
    public void setup() {
        servlet = new HttpServlet() {};
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        invocations = new ArrayList<OncePerRequestFilter>();
        filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                invocations.add(this);
                filterChain.doFilter(request, response);
            }
        };
    }

    @Test
    public void doFilterOnce() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        assertThat(invocations).containsOnly(filter);
    }

    @Test
    public void doFilterMultiOnlyIvokesOnce() throws ServletException, IOException {
        filter.doFilter(request, response, new MockFilterChain(servlet, filter));

        assertThat(invocations).containsOnly(filter);
    }

    @Test
    public void doFilterOtherSubclassInvoked() throws ServletException, IOException {
        OncePerRequestFilter filter2 = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                invocations.add(this);
                filterChain.doFilter(request, response);
            }
        };
        filter.doFilter(request, response, new MockFilterChain(servlet, filter2));

        assertThat(invocations).containsOnly(filter, filter2);
    }
}