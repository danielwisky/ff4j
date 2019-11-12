package org.ff4j.core;

/*-
 * #%L
 * ff4j-core
 * %%
 * Copyright (C) 2013 - 2019 FF4J
 * %%
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
 * #L%
 */

import static org.ff4j.core.utils.JsonUtils.attributeAsJson;
import static org.ff4j.core.utils.JsonUtils.objectAsJson;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.Predicate;

import org.ff4j.core.cache.CacheManager;
import org.ff4j.core.cache.CacheProxyFeatures;
import org.ff4j.core.cache.CacheProxyProperties;
import org.ff4j.core.config.FF4jConfiguration;
import org.ff4j.core.config.FF4jConfigurationParser;
import org.ff4j.event.Event;
import org.ff4j.event.repository.audit.AuditTrailRepository;
import org.ff4j.event.repository.hit.FeatureHitRepository;
import org.ff4j.event.repository.hit.FeatureHitRepositoryInMemory;
import org.ff4j.event.repository.hit.FeatureHitRepositoryListener;
import org.ff4j.event.repository.hit.FeatureHitRepositorySupport;
import org.ff4j.feature.Feature;
import org.ff4j.feature.exception.FeatureNotFoundException;
import org.ff4j.feature.repository.FeatureRepository;
import org.ff4j.feature.repository.FeatureRepositoryInMemory;
import org.ff4j.property.Property;
import org.ff4j.property.repository.PropertyRepository;
import org.ff4j.property.repository.PropertyRepositoryInMemory;
import org.ff4j.user.repository.RolesAndUsersRepository;
import org.ff4j.user.repository.RolesAndUsersRepositoryInMemory;

/**
 * Main class and public api to work with FF4j.
 * 
 * Instanciate this bean in you application to perform : 
 * - feature toggling through {@link FeatureRepository}
 * - Configuration and properties management with {@link PropertyRepository}
 * - Application monitoring with {@link FeatureHitRepository}
 * 
 * @author Cedrick Lunven (@clunven)
 *
 * @since 2.0
 */
public class FF4j extends FF4jRepositoryObserver < FeatureHitRepositoryListener > implements Predicate<String> {
    
    // -------------------------------------------------------------------------
    // ------------------- META-DATA         -----------------------------------
    // -------------------------------------------------------------------------
    
    /** Top for startup in  order to compute uptime. */
    private final long startTime = System.currentTimeMillis();

    /** Version of ff4j library. */
    private final String version = getClass().getPackage().getImplementationVersion();
    
    /** Source of events defined for monitoring purpose. */
    private Event.Source source = Event.Source.JAVA_API;
   
    /** Flag to ask for automatically create the feature if not found in the store. */
    private boolean autoCreateFeatures = false;
   
    // -------------------------------------------------------------------------
    // -------- Repositories (features, properties, events) --------------------
    // -------------------------------------------------------------------------
    
    /** Storage to persist feature within {@link FeatureRepository}. */
    private FeatureRepository repositoryFeatures = new FeatureRepositoryInMemory();
   
    /** Storage to persist properties within {@link PropertyRepository}. */
    private PropertyRepository repositoryProperties = new PropertyRepositoryInMemory();
    
    /** ReadOnly but can be extended to have full control on user (and dedicated screen in console). */
    private RolesAndUsersRepository repositoryUsersRoles = new RolesAndUsersRepositoryInMemory();
    
    /** Storage to persist event logs. */ 
    private Optional <AuditTrailRepository> auditTrail = Optional.empty();
   
    /** Define feature usage. */
    private FeatureHitRepositorySupport repositoryEventFeaturesUsage = new FeatureHitRepositoryInMemory();
    
    // -------------------------------------
    // ---------- CONTEXT ------------------
    // -------------------------------------
    
    /** Hold properties related to each users. */
    private ThreadLocal < FF4jContext > context = new ThreadLocal<>();
    
    // -------------------------------------
    // ---------- INIT ---------------------
    // -------------------------------------
    
    /**
     * Base constructor: Allows instantiation through Inversion of Control Ioc.
     * Default settings use InMemory stores and let them empty.
     */
    public FF4j() {}
    
    /**
     * Default parser and embedded in FF4jCore is still XML and V2, but you can still use v1 and v1 configuration files
     *
     * @param xmlFile
     *          Xml configuration file
     */
    public FF4j(FF4jConfigurationParser parser, String confFile) {
        this(parser.parse(confFile));
    }
    public FF4j(FF4jConfigurationParser parser, InputStream confFile) {
        this(parser.parse(confFile));
    }
    
    /**
     * Configuration loaded as an Object (from configuration file?)
     * 
     * @param config
     *      configuration bean for ff4j.
     */
    public FF4j(FF4jConfiguration config) {
        this.repositoryFeatures           = new FeatureRepositoryInMemory(config);
        this.repositoryProperties         = new PropertyRepositoryInMemory(config);
        this.repositoryUsersRoles         = new RolesAndUsersRepositoryInMemory(config);
        this.repositoryEventFeaturesUsage = new FeatureHitRepositoryInMemory();
    }
    
    // -------------------------------------
    // -------     PREDICATE        --------
    // -------------------------------------

    /** {@inheritDoc} */
    @Override
    public boolean test(String uid) {
        Feature feature;
        try {
            feature = getRepositoryFeatures().read(uid);
        } catch (FeatureNotFoundException fnfe) {
            if (this.autoCreateFeatures) {
                feature = new Feature(uid).toggleOff();
                getRepositoryFeatures().save(feature);
            } else {
                throw fnfe;
            }
        }
        boolean featureToggled = feature.isEnabled() && feature.isToggled(getContext());
        if (featureToggled) {
            final Feature featureNotif = feature;
            this.notify((listener) -> listener.onFeatureHit(featureNotif));
        }
        return featureToggled;
    }
    
    // ---------------------------------
    // ----------- OPERATIONS  ---------
    // ---------------------------------
    
    /**
     * Toggle on feature (even if already toggled)
     * 
     * @param uid
     *            unique feature identifier.
     */
    public FF4j toggleOn(String uid) {
        try {
            getRepositoryFeatures().toggleOn(uid);
        } catch (FeatureNotFoundException fnfe) {
            if (this.autoCreateFeatures) {
                getRepositoryFeatures().save(new Feature(uid).toggleOn());
            } else {
                throw fnfe;
            }
        }
        return this;
    }
    
    /**
     * Toggle on feature (even if not toggled)
    
     * 
     * @param uid
     *            unique feature identifier.
     */
    public FF4j toggleOff(String uid) {
        try {
            getRepositoryFeatures().toggleOff(uid);
        } catch (FeatureNotFoundException fnfe) {
             if (this.autoCreateFeatures) {
                 getRepositoryFeatures().save(new Feature(uid).toggleOff());
             } else {
                throw fnfe;
             }
        }
        return this;
    }  
    
    public FF4j withFeature(Feature fp) {
        getRepositoryFeatures().save(fp);
        return this; 
    }
    
    /**
     * Enable a cache proxy.
     * 
     * @param cm
     *      current cache manager
     * @return
     *      current ff4j bean
     */
    public FF4j withCaching(CacheManager<String, Feature> cm, CacheManager<String, Property<?>> pm) {
        withCachingFeatures(cm);
        withCachingProperties(pm);
        return this;
    }
    
    /**
     * Enable a cache for properties.
     * 
     * @param cm
     *      current cache manager
     * @return
     *      current ff4j bean
     */
    public FF4j withCachingProperties(CacheManager<String, Property<?>> pm) {
        setRepositoryProperties(new CacheProxyProperties(getRepositoryProperties(), pm));
        return this;
    }
    
    /**
     * Enable a cache for features.
     * 
     * @param cm
     *      current cache manager
     * @return
     *      current ff4j bean
     */
    public FF4j withCachingFeatures(CacheManager<String, Feature> cm) {
        setRepositoryFeatures(new CacheProxyFeatures(getRepositoryFeatures(), cm));
        return this;
    }
    
    // -------------------------
    // ------ Utilities --------
    // -------------------------
    
    /**
     * Create tables/collections/columns in DB (if required).
     */
    public void createSchema() {
    	// Features
        if (null != getRepositoryFeatures()) {
            getRepositoryFeatures().createSchema();
        }
        // Properties
        if (null != getRepositoryProperties()) {
            getRepositoryProperties().createSchema();
        }
        // FeatureUsage
        if (null != getRepositoryEventFeaturesUsage()) {
            getRepositoryEventFeaturesUsage().createSchema();
        }
        // AuditTrail
        if (!getAuditTrail().isEmpty()) {
            getAuditTrail().get().createSchema();
        }
        // Users
        if (null != getRepositoryUsersRoles()) {
            getRepositoryUsersRoles().createSchema();
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        long uptime = System.currentTimeMillis() - startTime;
        long daynumber = uptime / (1000 * 3600 * 24L);
        uptime = uptime - daynumber * 1000 * 3600 * 24L;
        long hourNumber = uptime / (1000 * 3600L);
        uptime = uptime - hourNumber * 1000 * 3600L;
        long minutenumber = uptime / (1000 * 60L);
        uptime = uptime - minutenumber * 1000 * 60L;
        long secondnumber = uptime / 1000L;
        sb.append("\"uptime\":\"");
        sb.append(daynumber + " day(s) ");
        sb.append(hourNumber + " hours(s) ");
        sb.append(minutenumber + " minute(s) ");
        sb.append(secondnumber + " seconds\"");
        sb.append(attributeAsJson("autocreate", autoCreateFeatures));
        sb.append(attributeAsJson("source", source));
        sb.append(attributeAsJson("version", version));
        if (getRepositoryFeatures() != null) {
            sb.append(objectAsJson("featuresStore", getRepositoryFeatures().toString()));
        }
        if (getRepositoryProperties() != null) {
            sb.append(objectAsJson("propertiesStore", getRepositoryProperties().toString()));
        }
        if (getRepositoryUsersRoles() != null) {
            sb.append(objectAsJson("userRolesStore", getRepositoryUsersRoles().toString()));
        }
        sb.append("}");
        return sb.toString();
    }
    
    // ---------------------------
    // ------ CACHE PROXY --------
    // ---------------------------
    
    public FeatureRepository getTargetRepositoryFeatures() {
        Optional<CacheProxyFeatures> cacheProxy = getRepositoryFeaturesCacheProxy();
        return cacheProxy.isPresent() ? cacheProxy.get().getTargetFeatureStore() : getRepositoryFeatures();
    }
    
    public Optional<CacheProxyFeatures> getRepositoryFeaturesCacheProxy() {
        FeatureRepository rf = getRepositoryFeatures();
        if (rf instanceof CacheProxyFeatures) {
            return Optional.of((CacheProxyFeatures)rf);
        }
        return Optional.empty();
    }
  
    public PropertyRepository getTargetRepositoryProperties() {
        Optional<CacheProxyProperties> cacheProxy = getRepositoryPropertiesCacheProxy();
        return cacheProxy.isPresent() ? cacheProxy.get().getTargetPropertyStore() : getRepositoryProperties();
    }
    
    public Optional<CacheProxyProperties> getRepositoryPropertiesCacheProxy() {
        PropertyRepository ps = getRepositoryProperties();
        if (ps instanceof CacheProxyProperties) {
            return Optional.of((CacheProxyProperties) ps);
        }
        return Optional.empty();
    }
    
    /**
     * Clear cache proxy and feature if relevant.
     *
     * @return
     *      true if a cache has been empty.
     */
    public boolean clearCache() {
        Optional<CacheProxyFeatures> cpFeatures     = getRepositoryFeaturesCacheProxy();
        Optional<CacheProxyProperties> cpProperties = getRepositoryPropertiesCacheProxy();
        boolean status = cpFeatures.isPresent() || cpProperties.isPresent();
        cpFeatures.ifPresent(cpf   -> cpf.getCacheManager().clear());
        cpProperties.ifPresent(cpp -> cpp.getCacheManager().clear());
        return status;
    }
    
    // -------------------------------------------------------------------------
    // ------------------- FEATURE STORE     -----------------------------------
    // -------------------------------------------------------------------------
    
    /**
     * Getter accessor for attribute 'repositoryFeatures'.
     *
     * @return
     *       current value of 'repositoryFeatures'
     */
    public FeatureRepository getRepositoryFeatures() {
        return repositoryFeatures;
    }

    /**
     * Setter accessor for attribute 'repositoryFeatures'.
     * @param repositoryFeatures
     *      new value for 'repositoryFeatures '
     */
    public void setRepositoryFeatures(FeatureRepository repositoryFeatures) {
        this.repositoryFeatures = repositoryFeatures;
    }
    
    /**
     * NON Static to be use by Injection of Control.
     * 
     * @param featureStore
     *            target store.
     */
    public FF4j withRepositoryFeatures(FeatureRepository featureStore) {
        setRepositoryFeatures(featureStore);
        return this;
    }
    
    // -------------------------------------------------------------------------
    // ------------------- PROPERTY STORE    -----------------------------------
    // -------------------------------------------------------------------------
    
    /**
     * Getter accessor for attribute 'repositoryProperties'.
     *
     * @return
     *       current value of 'repositoryProperties'
     */
    public PropertyRepository getRepositoryProperties() {
        return repositoryProperties;
    }

    /**
     * Setter accessor for attribute 'repositoryProperties'.
     * @param repositoryProperties
     *      new value for 'repositoryProperties '
     */
    public void setRepositoryProperties(PropertyRepository repositoryProperties) {
        this.repositoryProperties = repositoryProperties;
    }
    
    /**
     * Fluent Method to init ff4j.
     *
     * @param propertyStore
     *      current ff4j proposition
     * @return
     *      current ff4j instance
     */
    public FF4j withRepositoryProperties(PropertyRepository propertyStore) {
        setRepositoryProperties(propertyStore);
        return this;
    }
    
    // ----------------------------------------------------------------------
    // ------------------- USERS STORE    -----------------------------------
    // ----------------------------------------------------------------------
    
    /**
     * Getter accessor for attribute 'repositoryProperties'.
     *
     * @return
     *       current value of 'repositoryProperties'
     */
    public RolesAndUsersRepository getRepositoryUsersRoles() {
        return repositoryUsersRoles;
    }

    /**
     * Setter accessor for attribute 'repositoryProperties'.
     * @param repositoryProperties
     *      new value for 'repositoryProperties '
     */
    public void setRepositoryUsersRoles(RolesAndUsersRepository repositoryUsers) {
        this.repositoryUsersRoles = repositoryUsers;
    }
    
    /**
     * Fluent Method to init ff4j.
     *
     * @param propertyStore
     *      current ff4j proposition
     * @return
     *      current ff4j instance
     */
    public FF4j withRepositoryUsersRoles(RolesAndUsersRepository propertyStore) {
        setRepositoryUsersRoles(propertyStore);
        return this;
    }
    
    // -----------------------------------------------------------
    // ------------------- AUDIT TRAILS      ---------------------
    // -----------------------------------------------------------
    
    /**
     * Getter accessor for attribute 'auditTrail'.
     *
     * @return
     *       current value of 'auditTrail'
     */
    public Optional<AuditTrailRepository> getAuditTrail() {
        return auditTrail;
    }

    /**
     * Setter accessor for attribute 'auditTrail'.
     *
     * @param auditTrail
     *      new value for 'auditTrail '
     */
    public void setRepositoryAudit(AuditTrailRepository auditTrail) {
        withAudit(auditTrail);
    }
    
    /**
     * Update & enable audit trail information.
     *
     * @param auditTrail
     *      
     * @return
     */
    public FF4j withRepositoryAudit(AuditTrailRepository auditTrail) {
        setRepositoryAudit(auditTrail);
        return this;
    }
    
    /**
     * Get audit status.
     *
     * @return
     *      audit status.
     */
    public boolean isAuditEnabled() {
        return !getAuditTrail().isEmpty();
    }
    
    public FF4j withAudit(AuditTrailRepository auditTrailRepository) {
        this.auditTrail = Optional.ofNullable(auditTrailRepository);
        getRepositoryFeatures().registerAuditListener(getAuditTrail().get());
        getRepositoryProperties().registerAuditListener(getAuditTrail().get());
        return this;
    }
    /**
     * Unregister listener to work on audit.
     *
     * @return
     *      current ff4j instance
     */
    public FF4j stopAudit() {
        this.auditTrail = Optional.empty();
        getRepositoryFeatures().unRegisterAuditListener();
        getRepositoryProperties().unRegisterAuditListener();
        return this;
    }
    
    // -------------------------------------------------------------------------
    // ------------------- FEATURE USAGE     -----------------------------------
    // -------------------------------------------------------------------------
    
    /**
     * Enable features tracking (enable/disable/check).
     * 
     * @param featureUsage
     * @return
     */
    public FF4j withRepositoryEventFeaturesUsage(FeatureHitRepositorySupport featureUsage) {
        setRepositoryEventFeaturesUsage(featureUsage);
        return withFeatureUsageTracking();
    }
    
    /**
     * Register listener for feature usage.
     *
     * @return
     *      current ff4j instance
     */
    public FF4j withFeatureUsageTracking() {
        registerListener(FeatureHitRepositoryListener.KEY_USAGETRACKING_LISTENER, getRepositoryEventFeaturesUsage());
        return this;
    }
    
    /**
     * Unregister listener for feature usage.
     *
     * @return
     *      current ff4j instance
     */
    public FF4j withoutFeatureUsageTracking() {
        unregisterListener(FeatureHitRepositoryListener.KEY_USAGETRACKING_LISTENER);
        return this;
    }

    /**
     * Getter accessor for attribute 'repositoryEventFeaturesUsage'.
     *
     * @return
     *       current value of 'repositoryEventFeaturesUsage'
     */
    public FeatureHitRepositorySupport getRepositoryEventFeaturesUsage() {
        return repositoryEventFeaturesUsage;
    }

    /**
     * Setter accessor for attribute 'repositoryEventFeaturesUsage'.
     * @param repositoryEventFeaturesUsage
     * 		new value for 'repositoryEventFeaturesUsage '
     */
    public void setRepositoryEventFeaturesUsage(FeatureHitRepositorySupport repositoryEventFeaturesUsage) {
        this.repositoryEventFeaturesUsage = repositoryEventFeaturesUsage;
    }
    
    // -------------------------------------------------------------------------
    // ------------------- GETTERS & SETTERS -----------------------------------
    // -------------------------------------------------------------------------
     
    // --- Autocreate
    
    /**
     * Enable autocreate for features.
     *
     * @return
     *      current instance
     */
    public FF4j withFeatureAutoCreate() {
        setAutoCreateFeatures(true);
        return this;
    }
    
    /**
     * Getter accessor for attribute 'autoCreateFeatures'.
     *
     * @return
     *       current value of 'autoCreateFeatures'
     */
    public boolean isAutoCreateFeatures() {
        return autoCreateFeatures;
    }

    /**
     * Setter accessor for attribute 'autoCreateFeatures'.
     * @param autoCreateFeatures
     *      new value for 'autoCreateFeatures '
     */
    public void setAutoCreateFeatures(boolean autoCreateFeatures) {
        this.autoCreateFeatures = autoCreateFeatures;
    }
    
    // --- Context
    
    /**
     * Obtain the current <code>FF4jExecutionContext</code>.
     *
     * @return the security context (never <code>null</code>)
     */
    public FF4jContext getContext() {
        if (null == context.get()) {
            context.set(new FF4jContext(this));
        }
        return context.get();
    }
    
    /**
     * Obtain the current <code>FF4jExecutionContext</code>.
     *
     * @return the security context (never <code>null</code>)
     */
    public void setContext(FF4jContext pcontext) {
        context.set(pcontext);
    }

    /**
     * Obtain the current <code>FF4jExecutionContext</code>.
     *
     * @return the security context (never <code>null</code>)
     */
    public void add2Context(FF4jContext pcontext) {
        getContext().getParameters().putAll(pcontext.getParameters());
    }
    
    /**
     * Explicitly clears the context value from the current thread.
     */
    public void clearContext() {
        context.remove();
    }

    // --- Version
    
    /**
     * Getter accessor for attribute 'version'.
     *
     * @return
     *       current value of 'version'
     */
    public String getVersion() {
        return version;
    }   
    
    // --- Source

    /**
     * Getter accessor for attribute 'source'.
     *
     * @return
     *       current value of 'source'
     */
    public Event.Source getSource() {
        return source;
    }
    
    /**
     * Setter accessor for attribute 'source'.
     * 
     * @param source
     *      new value for 'source '
     */
    public void setSource(Event.Source source) {
        this.source = source;
    }
    
    /**
     * Fluent API to get source.
     *
     * @param source
     *      current source
     * @return
     */
    public FF4j withSource(Event.Source source) {
        setSource(source);
        return this;
    }
   
    /**
     * Getter accessor for attribute 'startTime'.
     *
     * @return
     *       current value of 'startTime'
     */
    public long getStartTime() {
        return startTime;
    }
    
    /**
     * Dispaly uptime for ff4j.
     *
     * @return
     *      current update in millis
     */
    public long getUptime() {
        return System.currentTimeMillis() - getStartTime();
    }    
    
}