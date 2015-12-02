package org.ff4j.drools;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.drools.core.ClockType;
import org.ff4j.core.FeatureStore;
import org.ff4j.core.FlippingExecutionContext;
import org.kie.api.KieServices;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.utils.KieHelper;

/**
 * Singleton pattern to instanciate drool object once and reuse for each
 * feature. We assuming that spring do not autowired drools element into
 * Features and we need to wrap it.
 *
 * @author Cedrick Lunven (@clunven)</a>
 */
public final class FF4JDroolsService implements Serializable {
    
    /** Serial. */
    private static final long serialVersionUID = -4732368029311891671L;

    /** protected instance. */
    private static FF4JDroolsService _instance;
    
    /** Drools service must be singleton . */
    private KieServices kieServices;

    /** Container for sessions. */
    private KieContainer kieContainer;

    /** Working Session. */
    private KieSession ksession;

    /** target drools base name. */
    private String basename;

    /** list of files. */
    private Set<String> ruleFiles = new HashSet<>();
    
    /**
     * Implementation of singleton pattern (Hide Constructor).
     */
    private FF4JDroolsService() {
    }
    
    /**
     * Check singleton.
     *
     * @return
     *      singleton already created.
     */
    public static synchronized boolean isInitialized() {
        return (_instance != null && _instance.ksession != null);
    }
    
    /**
     * Implementation of singleton pattern.
     *
     * @return
     */
    public static synchronized FF4JDroolsService getInstance() {
        if (!isInitialized()) {
            throw new IllegalStateException("The service has not been initialized yet, "
                    + "please init with initFromBaseName() or initFromRulesFiles()");
        }
        return _instance;
    }
    
    /**
     * Drools expects to find the 'kmodule.xml' file in src/main/resources/META-INF.
     * It must contain a definition of kbase and kession with same base name exemple :
     * 
     * <?xml version="1.0" encoding="UTF-8"?>
     * <kmodule xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://jboss.org/kie/6.0.0/kmodule">
     *   <kbase name="whatever_you_want" packages="org...">
     *     <ksession name="ff4jDroolsStrategy"/>
     *   </kbase>
     * </kmodule>
     */
    public static synchronized void initFromBaseName(String baseName) {
        if (isInitialized()) {
            throw new IllegalStateException("This Factory has already be initialized once");
        }
        _instance               = new FF4JDroolsService();
        _instance.basename      = baseName; 
        _instance.kieServices   = KieServices.Factory.get();
        _instance.kieContainer  = _instance.kieServices.newKieClasspathContainer();
        _instance.ksession      = _instance.kieContainer.newKieSession(baseName);
        
        if (_instance.ksession == null) {
            throw new IllegalArgumentException("Cannot find kName " + baseName + " , check kmodule.xml file.");
        }
    }
    
    /**
     * Initialisation of Drools stateful session without convention and kodmule files.
     *
     * @param ruleFiles
     *      DRL files
     */
    public static synchronized void initFromRulesFiles(Set < String > ruleFiles) {
        if (isInitialized()) {
            throw new IllegalStateException("This Factory has already be initialized once");
        }
        _instance               = new FF4JDroolsService();
        _instance.ruleFiles     = ruleFiles; 
        
        KieHelper helper = new KieHelper();
        KieSessionConfiguration sessionConfig = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();
        sessionConfig.setOption(ClockTypeOption.get(ClockType.PSEUDO_CLOCK.getId()));
        
        ruleFiles.stream().forEach(drlFile -> helper.addContent(loadResourceAsString(drlFile),
                                             ResourceType.determineResourceType(drlFile)));
        _instance.ksession = helper.build(EventProcessingOption.STREAM).newKieSession(sessionConfig, null);
    }
    
    /** {@inheritDoc} */
    public boolean evaluate(FF4jDroolsRequest request) {
        /*
         * To retrieve result for rules execution there are 2 ways: - Modifed an existing fact - Retrieve FacHandler from session
         * : session.getFactHandles(filter)
         * 
         * FF4J expects the fact {@link FF4JDroolsRequest} to be modified by the target rules. By default the status is 'false'.
         */        
        _instance.ksession.setGlobal("store", request.getFeatureStore());

        // FactHandle drHandler = ksession.insert(droolsRequest);
        _instance.ksession.insert(request);

        // Execute the rules
        ksession.fireAllRules();

        // Empty the stateful session
        _instance.ksession.dispose();

        return request.isToggled();
    }
    
    /**
     * Load classpath resource as String (here DRL)
     * 
     * @param resourceName
     *      target resources
     * @return
     *      file content as string
     */
    private final static String loadResourceAsString(final String resourceName) {
        try {
            InputStream rin = ClassLoader.getSystemResourceAsStream(resourceName);
            StringBuffer out = new StringBuffer();
            byte[] b = new byte[4096];
            int n = 0;
            while (n != -1) {
                out.append(new String(b, 0, n));
                n = rin.read(b);
            }
            return out.toString();
        } catch (IOException ioe) {
            throw new IllegalArgumentException("Cannot read resource " + resourceName, ioe);
        }
    }

    /**
     * Getter accessor for attribute 'basename'.
     *
     * @return
     *       current value of 'basename'
     */
    public String getBasename() {
        return basename;
    }

    /**
     * Getter accessor for attribute 'kieServices'.
     *
     * @return
     *       current value of 'kieServices'
     */
    public KieServices getKieServices() {
        return kieServices;
    }

    /**
     * Getter accessor for attribute 'ruleFiles'.
     *
     * @return
     *       current value of 'ruleFiles'
     */
    public Set<String> getRuleFiles() {
        return ruleFiles;
    }

}
