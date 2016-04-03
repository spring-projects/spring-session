package org.springframework.session.data.couchbase.utils

class ApplicationInstance {

    private Class<?> runnerClass
    private Object runnerInstance

    ApplicationInstance(Class<?> runnerClass, Object runnerInstance) {
        this.runnerClass = runnerClass
        this.runnerInstance = runnerInstance
    }

    Class<?> getRunnerClass() {
        return runnerClass
    }

    Object getRunnerInstance() {
        return runnerInstance
    }
}
