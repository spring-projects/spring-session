/*
 * Copyright 2002-2015 the original author or authors.
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
import geb.*
import spock.lang.*

/**
 * https://github.com/kensiprell/geb-multibrowser
 */
abstract class MultiBrowserGebSpec extends Specification {
    String gebConfEnv = null
    String gebConfScript = null

    // Map of geb browsers which can be referenced by name in the spec
    // THese currently share the same config.  This is not a problem for
    // my uses, but I can see potential for wanting to configure different
    // browsers separately
    @Shared _browsers = createBrowserMap()
    def currentBrowser

    def createBrowserMap() {
        [:].withDefault { new Browser(createConf()) }
    }

    Configuration createConf() {
        // Use the standard configured geb driver, but turn off cacheing so
        // we can run multiple
        def conf = new ConfigurationLoader(gebConfEnv).getConf(gebConfScript)
        conf.cacheDriver = false
        return conf
    }

    def withBrowserSession(browser, Closure c) {
        currentBrowser = browser
        def returnedValue = c.call()
        currentBrowser = null
        returnedValue
    }

    void resetBrowsers() {
        _browsers.each { k, browser ->
            if (browser.config?.autoClearCookies) {
                browser.clearCookiesQuietly()
            }
            browser.quit()
        }
        _browsers = createBrowserMap()
    }

    def propertyMissing(String name) {
        if(currentBrowser) {
            return currentBrowser."$name"
        } else {
            return _browsers[name]
        }
    }

    def methodMissing(String name, args) {
        if(currentBrowser) {
            return currentBrowser."$name"(*args)
        } else {
            def browser = _browsers[name]
            if(args) {
                return browser."${args[0]}"(*(args[1..-1]))
            } else {
                return browser
            }
        }
    }

    def propertyMissing(String name, value) {
        if(!currentBrowser) throw new IllegalArgumentException("No context for setting property $name")
        currentBrowser."$name" = value
    }

    private isSpecStepwise() {
        this.class.getAnnotation(Stepwise) != null
    }

    def cleanup() {
        if (!isSpecStepwise()) resetBrowsers()
    }

    def cleanupSpec() {
        if (isSpecStepwise()) resetBrowsers()
    }
}