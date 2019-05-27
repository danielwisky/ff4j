package org.ff4j.couchdb.store;

import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDOUT;

/*
 * #%L
 * ff4j-store-couchbase
 * %%
 * Copyright (C) 2013 - 2017 FF4J
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

import org.ektorp.CouchDbConnector;
import org.ff4j.core.FeatureStore;
import org.ff4j.couchdb.CouchDbConnection;
import org.ff4j.couchdb.CouchDbFeatureView;
import org.ff4j.test.store.FeatureStoreTestSupport;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * CouchDb Feature Store Tests.
 *
 * @author Curtis White (@drizztguen77)
 */
public class FeatureStoreCouchDbTest  {

    /**
     * Reuse connection for tests.
     *
    private static CouchDbConnection conn = null;
    private static CouchDbFeatureView repo = null;
    private static int COUCHDB_PORT = 5984;
    private static String TEST_USER = "ff4j";
    private static String TEST_PASSWORD = "ff4j";
    private static String DB_NAME = "ff4j";


    @ClassRule
    public static GenericContainer<?> couchdb = new GenericContainer<>("couchdb:latest")
            .withExposedPorts(COUCHDB_PORT)
            .withEnv("COUCHDB_USER", TEST_USER)
            .withEnv("COUCHDB_PASSWORD", TEST_PASSWORD)


    @Override
    protected FeatureStore initStore() {
        return null;
        /*WaitingConsumer consumer = new WaitingConsumer();
        couchdb.followOutput(consumer, STDOUT, STDERR);

        if (conn == null) {

            conn = new CouchDbConnection()
                    .dbName(DB_NAME)
                    .userName(TEST_USER)
                    .password(TEST_PASSWORD)
                    .host(couchdb.getContainerIpAddress())
                    .port(couchdb.getFirstMappedPort())
                    .createDatabaseIfNotExists(true)
            ;
        }

        CouchDbConnector connector = conn.getCouchDbConnector();

        if (repo == null) {
            repo = new CouchDbFeatureView(connector);
        }

        FeatureStoreCouchDb store = new FeatureStoreCouchDb(conn, repo);
        store.clear();
        store.importFeaturesFromXmlFile("test-ff4j-features.xml");
        return store;
    }*/
}
