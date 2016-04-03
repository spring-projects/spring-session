package org.springframework.session.data.couchbase;

import org.slf4j.Logger;
import org.springframework.session.ExpiringSession;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.round;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.unmodifiableSet;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;
import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.isTrue;

public class CouchbaseSession implements ExpiringSession, Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CREATION_TIME_ATTRIBUTE = "$creationTime";
    public static final String LAST_ACCESSED_TIME_ATTRIBUTE = "$lastAccessedTime";
    public static final String MAX_INACTIVE_INTERVAL_ATTRIBUTE = "$maxInactiveInterval";
    protected static final String GLOBAL_ATTRIBUTE_NAME_PREFIX = CouchbaseSession.class.getName() + ".global.";

    private static final Logger log = getLogger(CouchbaseSession.class);

    protected String id = randomUUID().toString();
    protected Map<String, Object> globalAttributes = new HashMap<String, Object>();
    protected Map<String, Object> namespaceAttributes = new HashMap<String, Object>();
    protected boolean namespacePersistenceRequired = false;
    protected boolean principalSession = false;

    public CouchbaseSession(int timeoutInSeconds) {
        long now = currentTimeMillis();
        setCreationTime(now);
        setLastAccessedTime(now);
        setMaxInactiveIntervalInSeconds(timeoutInSeconds);
    }

    public CouchbaseSession(String id, Map<String, Object> globalAttributes, Map<String, Object> namespaceAttributes) {
        this.id = id;
        this.globalAttributes = globalAttributes == null ? new HashMap<String, Object>() : globalAttributes;
        this.namespaceAttributes = namespaceAttributes == null ? new HashMap<String, Object>() : namespaceAttributes;
        if (containsPrincipalAttribute()) {
            principalSession = true;
        }
    }

    public static String globalAttributeName(String attributeName) {
        return GLOBAL_ATTRIBUTE_NAME_PREFIX + attributeName;
    }

    public long getCreationTime() {
        return round((Double) globalAttributes.get(CREATION_TIME_ATTRIBUTE));
    }

    public long getLastAccessedTime() {
        return round((Double) globalAttributes.get(LAST_ACCESSED_TIME_ATTRIBUTE));
    }

    public void setLastAccessedTime(long lastAccessedTime) {
        globalAttributes.put(LAST_ACCESSED_TIME_ATTRIBUTE, lastAccessedTime);
    }

    public void setMaxInactiveIntervalInSeconds(int interval) {
        globalAttributes.put(MAX_INACTIVE_INTERVAL_ATTRIBUTE, interval);
    }

    public int getMaxInactiveIntervalInSeconds() {
        return (Integer) globalAttributes.get(MAX_INACTIVE_INTERVAL_ATTRIBUTE);
    }

    public boolean isExpired() {
        return getMaxInactiveIntervalInSeconds() >= 0 && currentTimeMillis() - SECONDS.toMillis(getMaxInactiveIntervalInSeconds()) >= getLastAccessedTime();
    }

    public String getId() {
        return id;
    }

    @SuppressWarnings("unchecked")

    public <T> T getAttribute(String attributeName) {
        checkAttributeName(attributeName);
        T attribute;
        if (isGlobal(attributeName)) {
            String name = getNameFromGlobalName(attributeName);
            attribute = (T) globalAttributes.get(name);
            log.trace("Read global HTTP session attribute: [name='{}', value={}]", name, attribute);
        } else {
            attribute = (T) namespaceAttributes.get(attributeName);
            log.trace("Read application namespace HTTP session attribute: [name='{}', value={}]", attributeName, attribute);
        }
        return attribute;
    }

    public Set<String> getAttributeNames() {
        Set<String> attributesNames = new HashSet<String>();
        for (String attributeName : globalAttributes.keySet()) {
            attributesNames.add(globalAttributeName(attributeName));
        }
        attributesNames.addAll(namespaceAttributes.keySet());
        return unmodifiableSet(attributesNames);
    }

    public void setAttribute(String attributeName, Object attributeValue) {
        checkAttributeName(attributeName);
        if (isGlobal(attributeName)) {
            String name = getNameFromGlobalName(attributeName);
            if (PRINCIPAL_NAME_INDEX_NAME.equals(name)) {
                principalSession = true;
            }
            globalAttributes.put(name, attributeValue);
            log.trace("Set global HTTP session attribute: [name='{}', value={}]", name, attributeValue);
        } else {
            if (PRINCIPAL_NAME_INDEX_NAME.equals(attributeName)) {
                principalSession = true;
            }
            namespacePersistenceRequired = true;
            namespaceAttributes.put(attributeName, attributeValue);
            log.trace("Set application namespace HTTP session attribute: [name='{}', value={}]", attributeName, attributeValue);
        }
    }

    public void removeAttribute(String attributeName) {
        checkAttributeName(attributeName);
        if (isGlobal(attributeName)) {
            String name = getNameFromGlobalName(attributeName);
            globalAttributes.remove(name);
            log.trace("Removed global HTTP session attribute: [name='{}']", name);
        } else {
            namespacePersistenceRequired = true;
            namespaceAttributes.remove(attributeName);
            log.trace("Removed application namespace HTTP session attribute: [name='{}']", attributeName);
        }
    }

    public Map<String, Object> getGlobalAttributes() {
        return globalAttributes;
    }

    public Map<String, Object> getNamespaceAttributes() {
        return namespaceAttributes;
    }

    public boolean isNamespacePersistenceRequired() {
        return namespacePersistenceRequired;
    }

    public boolean isPrincipalSession() {
        return isNotBlank(getPrincipalAttribute());
    }

    public String getPrincipalAttribute() {
        Object principal = globalAttributes.get(PRINCIPAL_NAME_INDEX_NAME);
        if (principal == null) {
            principal = namespaceAttributes.get(PRINCIPAL_NAME_INDEX_NAME);
        }
        return (String) principal;
    }

    protected void setCreationTime(long creationTime) {
        globalAttributes.put(CREATION_TIME_ATTRIBUTE, creationTime);
    }

    protected void checkAttributeName(String attributeName) {
        hasText(attributeName, "Empty HTTP session attribute name");
        isTrue(!attributeName.trim().equals(GLOBAL_ATTRIBUTE_NAME_PREFIX), "Empty HTTP session global attribute name");
    }

    protected boolean isGlobal(String attributeName) {
        return attributeName.startsWith(GLOBAL_ATTRIBUTE_NAME_PREFIX);
    }

    protected String getNameFromGlobalName(String globalAttributeName) {
        return removeStart(globalAttributeName, GLOBAL_ATTRIBUTE_NAME_PREFIX);
    }

    protected boolean containsPrincipalAttribute() {
        return globalAttributes.containsKey(PRINCIPAL_NAME_INDEX_NAME) || namespaceAttributes.containsKey(PRINCIPAL_NAME_INDEX_NAME);
    }
}
