/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import javax.servlet.http.HttpSession;

import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.query.Index;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.gemfire.IndexFactoryBean;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.ObjectUtils;

/**
 * The SessionAttributesIndexFactoryBean class is a Spring {@link FactoryBean} that creates a GemFire {@link Index}
 * on the session attributes of the {@link HttpSession} object.
 *
 * @author John Blum
 * @since 1.3.0
 * @see org.springframework.beans.factory.BeanFactoryAware
 * @see org.springframework.beans.factory.BeanNameAware
 * @see org.springframework.beans.factory.FactoryBean
 * @see org.springframework.beans.factory.InitializingBean
 * @see com.gemstone.gemfire.cache.query.Index
 */
public class SessionAttributesIndexFactoryBean implements FactoryBean<Index>,
		InitializingBean, BeanFactoryAware, BeanNameAware {

	protected static final String[] DEFAULT_INDEXABLE_SESSION_ATTRIBUTES = {};

	private BeanFactory beanFactory;

	private GemFireCache gemfireCache;

	private Index sessionAttributesIndex;

	private String beanName;
	private String regionName;

	private String[] indexableSessionAttributes;

	/* (non-Javadoc) */
	public void afterPropertiesSet() throws Exception {
		if (isIndexableSessionAttributesConfigured()) {
			this.sessionAttributesIndex = newIndex();
		}
	}

	/**
	 * Determines whether any indexable Session attributes were configured for this {@link FactoryBean}.
	 *
	 * @return a boolean value indicating whether any indexable Session attributes were configured
	 * for this {@link FactoryBean}
	 * @see #setIndexableSessionAttributes(String[])
	 */
	protected boolean isIndexableSessionAttributesConfigured() {
		return !ObjectUtils.isEmpty(this.indexableSessionAttributes);
	}

	/**
	 * Constructs a GemFire {@link Index} over the attributes of the {@link HttpSession}.
	 *
	 * @return a GemFire {@link Index} over the {@link HttpSession} attributes.
	 * @throws Exception if an error occurs while initializing the GemFire {@link Index}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 */
	protected Index newIndex() throws Exception {
		IndexFactoryBean indexFactory = new IndexFactoryBean();

		indexFactory.setBeanFactory(this.beanFactory);
		indexFactory.setBeanName(this.beanName);
		indexFactory.setCache(this.gemfireCache);
		indexFactory.setName("sessionAttributesIndex");
		indexFactory.setExpression(String.format("s.attributes[%1$s]",
			getIndexableSessionAttributesAsGemFireIndexExpression()));
		indexFactory.setFrom(String.format("%1$s s", GemFireUtils.toRegionPath(this.regionName)));
		indexFactory.setOverride(true);
		indexFactory.afterPropertiesSet();

		return indexFactory.getObject();
	}

	/**
	 * Gets the names of all Session attributes that will be indexed by GemFire as single, comma-delimited
	 * String value constituting the Index expression of the Index definition.
	 *
	 * @return a String composed of all the named Session attributes for which GemFire
	 * will create an Index as an Index definition expression. If the indexable Session
	 * attributes were not configured, then the wildcard ("*") is returned.
	 * @see com.gemstone.gemfire.cache.query.Index#getIndexedExpression()
	 */
	protected String getIndexableSessionAttributesAsGemFireIndexExpression() {
		StringBuilder builder = new StringBuilder();

		for (String sessionAttribute : getIndexableSessionAttributes()) {
			builder.append(builder.length() > 0 ? ", " : "");
			builder.append(String.format("'%s'", sessionAttribute));
		}

		String indexExpression = builder.toString();

		return (indexExpression.isEmpty() ? "*" : indexExpression);
	}

	/* (non-Javadoc) */
	public Index getObject() throws Exception {
		return this.sessionAttributesIndex;
	}

	/* (non-Javadoc) */
	public Class<?> getObjectType() {
		return (this.sessionAttributesIndex != null ? this.sessionAttributesIndex.getClass() : Index.class);
	}

	/* (non-Javadoc) */
	public boolean isSingleton() {
		return true;
	}

	/* (non-Javadoc) */
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/* (non-Javadoc) */
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	/* (non-Javadoc) */
	public void setGemFireCache(GemFireCache gemfireCache) {
		this.gemfireCache = gemfireCache;
	}

	/* (non-Javadoc) */
	public void setIndexableSessionAttributes(String[] indexableSessionAttributes) {
		this.indexableSessionAttributes = indexableSessionAttributes;
	}

	/* (non-Javadoc) */
	protected String[] getIndexableSessionAttributes() {
		return (this.indexableSessionAttributes != null ? this.indexableSessionAttributes
			: DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);
	}

	/* (non-Javadoc) */
	public void setRegionName(String regionName) {
		this.regionName = regionName;
	}

	/* (non-Javadoc) */
	protected String getRegionName() {
		return this.regionName;
	}
}
