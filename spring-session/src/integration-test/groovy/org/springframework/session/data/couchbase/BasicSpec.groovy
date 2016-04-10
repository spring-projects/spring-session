package org.springframework.session.data.couchbase

import com.couchbase.client.java.query.N1qlQueryResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext
import org.springframework.boot.test.WebIntegrationTest
import org.springframework.data.couchbase.core.CouchbaseTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.session.data.couchbase.application.content.Message
import org.springframework.session.data.couchbase.utils.ApplicationInstance
import org.springframework.session.data.couchbase.utils.ApplicationInstanceRunner
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification

import static com.couchbase.client.java.query.N1qlQuery.simple
import static java.net.HttpCookie.parse
import static org.springframework.session.data.couchbase.application.content.CouchbaseConfiguration.COUCHBASE_BUCKET_NAME

@WebIntegrationTest(randomPort = true)
abstract class BasicSpec extends Specification {

    private static boolean bucketIndexCreated = false

    @Shared
    private RestTemplate rest = new RestTemplate()
    @Autowired(required = false)
    private CouchbaseTemplate couchbase
    @Autowired
    private EmbeddedWebApplicationContext context
    private int extraInstancePort
    private ApplicationInstance instance
    // Cannot store cookie in thread local because some tests starts more than one app instance. CANNOT run tests in parallel.
    private String currentSessionCookie

    void setup() {
        createBucketIndex()
        clearBucket()
    }

    void cleanup() {
        clearSessionCookie()
        stopApplication()
    }

    protected void startApplication(Class<?> applicationClass) {
        URL[] urls = [new File('/build/classes/test').toURI().toURL()]
        def classLoader = new URLClassLoader(urls, getClass().classLoader)
        def runnerClass = classLoader.loadClass(ApplicationInstanceRunner.class.name)
        def runnerInstance = runnerClass.newInstance()
        instance = new ApplicationInstance(runnerClass, runnerInstance)
        runnerClass.getMethod('setTestApplicationClass', Class).invoke(runnerInstance, applicationClass)
        runnerClass.getMethod('run').invoke(runnerInstance)
        extraInstancePort = runnerClass.getMethod('getPort').invoke(runnerInstance) as int
    }

    protected void stopApplication() {
        if (instance) {
            instance.runnerClass.getMethod('stop').invoke(instance.runnerInstance)
            instance = null
        }
    }

    protected boolean currentSessionExists() {
        return couchbase.exists(getCurrentSessionId())
    }

    protected void setSessionAttribute(Message attribute) {
        post('session/attribute', attribute, getPort())
    }

    protected void deleteSessionAttribute() {
        delete('session/attribute', getPort())
    }

    protected ResponseEntity<Message> getSessionAttribute() {
        return get('session/attribute', Message, getPort())
    }

    protected void setSessionBean(Message attribute) {
        post('session/bean', attribute)
    }

    protected ResponseEntity<Message> getSessionBean() {
        return get('session/bean', Message)
    }

    protected void invalidateSession() {
        delete('session')
    }

    protected String setPrincipalSessionAttribute() {
        return post('session/principal', null, getPort(), String).body
    }

    protected String setPrincipalSessionAttributeToExtraInstance() {
        return post('session/principal', null, extraInstancePort, String).body
    }

    protected ResponseEntity<List<String>> getPrincipalSessions() {
        return get('session/principal', List, getPort())
    }

    protected void clearSessionCookie() {
        currentSessionCookie = null
    }

    protected void clearBucket() {
        if (couchbase) {
            def result = couchbase.queryN1QL(simple("DELETE FROM $COUCHBASE_BUCKET_NAME"))
            failOnErrors(result)
        }
    }

    private void createBucketIndex() {
        if (!bucketIndexCreated && couchbase) {
            def result = couchbase.queryN1QL(simple('SELECT * FROM system:indexes'))
            failOnErrors(result)
            if (result.allRows().empty) {
                result = couchbase.queryN1QL(simple("CREATE PRIMARY INDEX ON $COUCHBASE_BUCKET_NAME USING GSI"))
                failOnErrors(result)
            }
            bucketIndexCreated = true
        }
    }

    private static void failOnErrors(N1qlQueryResult result) {
        if (!result.finalSuccess()) {
            throw new RuntimeException(result.errors().toString())
        }
    }

    private String getCurrentSessionId() {
        return parse(currentSessionCookie)[0].value
    }

    private <T> ResponseEntity<T> post(String path, Object body, int port = getPort(), Class<T> responseType = Object) {
        def url = createUrl(path, port)
        HttpHeaders headers = addSessionCookie()
        def request = new HttpEntity<>(body, headers)
        def response = rest.postForEntity(url, request, responseType)
        saveSessionCookie(response)
        return response
    }

    private <T> ResponseEntity<T> get(String path, Class<T> responseType, int port = getPort()) {
        def url = createUrl(path, port)
        HttpHeaders headers = addSessionCookie()
        def request = new HttpEntity<>(headers)
        def response = rest.exchange(url, HttpMethod.GET, request, responseType) as ResponseEntity<T>
        saveSessionCookie(response)
        return response
    }

    private void delete(String path, int port = getPort()) {
        def url = createUrl(path, port)
        HttpHeaders headers = addSessionCookie()
        def request = new HttpEntity<>(headers)
        def response = rest.exchange(url, HttpMethod.DELETE, request, Object)
        saveSessionCookie(response)
    }

    private static GString createUrl(String path, int port) {
        return "http://localhost:$port/$path"
    }

    private int getPort() {
        return context.embeddedServletContainer.port
    }

    private HttpHeaders addSessionCookie() {
        def headers = new HttpHeaders()
        headers.set(HttpHeaders.COOKIE, currentSessionCookie)
        return headers
    }

    private void saveSessionCookie(ResponseEntity response) {
        def cookie = response.headers.get('Set-Cookie')
        if (cookie != null) {
            currentSessionCookie = cookie
        }
    }
}
