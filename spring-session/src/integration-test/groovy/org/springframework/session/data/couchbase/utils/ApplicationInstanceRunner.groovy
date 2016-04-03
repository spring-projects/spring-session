package org.springframework.session.data.couchbase.utils

import org.springframework.boot.SpringApplication
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext
import org.springframework.session.data.couchbase.application.DefaultTestApplication

class ApplicationInstanceRunner {

    private final Object monitor = new Object()
    private EmbeddedWebApplicationContext context
    private boolean shouldWait
    private String namespace
    private boolean principalSessionsEnabled
    private int port

    void run() {
        if (context != null) {
            throw new IllegalStateException('Application context must be null to run this instance')
        }
        runInstance()
        waitInstanceIsStarted()
    }

    void stop() {
        SpringApplication.exit(context)
        context = null
    }

    void setNamespace(String namespace) {
        this.namespace = namespace
    }

    void setPrincipalSessionsEnabled(boolean principalSessionsEnabled) {
        this.principalSessionsEnabled = principalSessionsEnabled
    }

    int getPort() {
        return port
    }

    private void waitInstanceIsStarted() {
        synchronized (monitor) {
            if (shouldWait) {
                monitor.wait()
            }
        }
    }

    private void runInstance() {
        def runnerThread = new InstanceRunningThread()
        shouldWait = true
        runnerThread.contextClassLoader = DefaultTestApplication.classLoader
        runnerThread.start()
    }

    private class InstanceRunningThread extends Thread {

        @Override
        public void run() {
            context = SpringApplication.run(DefaultTestApplication, '--server.port=0', "--session-couchbase.persistent.namespace=$namespace", "--session-couchbase.persistent.principal-sessions.enabled=$principalSessionsEnabled") as EmbeddedWebApplicationContext
            port = context.embeddedServletContainer.port
            synchronized (monitor) {
                shouldWait = false
                monitor.notify()
            }
        }
    }
}
