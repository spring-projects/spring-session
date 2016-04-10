package org.springframework.session.data.couchbase.utils

import org.springframework.boot.SpringApplication
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext

class ApplicationInstanceRunner {

    private final Object monitor = new Object()
    private EmbeddedWebApplicationContext context
    private boolean shouldWait
    private Class<?> testApplicationClass
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

    void setTestApplicationClass(Class<?> testApplicationClass) {
        this.testApplicationClass = testApplicationClass
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
        def runnerThread = new InstanceRunningThread(testApplicationClass)
        shouldWait = true
        runnerThread.contextClassLoader = testApplicationClass.classLoader
        runnerThread.start()
    }

    private class InstanceRunningThread extends Thread {

        private final Class<?> testApplicationClass

        InstanceRunningThread(Class<?> testApplicationClass) {
            this.testApplicationClass = testApplicationClass
        }

        @Override
        public void run() {
            context = SpringApplication.run(testApplicationClass, '--server.port=0') as EmbeddedWebApplicationContext
            port = context.embeddedServletContainer.port
            synchronized (monitor) {
                shouldWait = false
                monitor.notify()
            }
        }
    }
}
