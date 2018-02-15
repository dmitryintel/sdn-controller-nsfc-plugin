/*******************************************************************************
 * Copyright (c) Intel Corporation
 * Copyright (c) 2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.osc.controller.nsfc;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.*;

import java.util.Map;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.PathUtils;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.sdk.controller.api.SdnControllerApi;
import org.osc.sdk.controller.api.SdnRedirectionApi;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.VirtualizationConnectorElement;
import org.osgi.framework.BundleContext;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class OSGiIntegrationTest {

    private static final String DOMAIN_NAME = "default";
    private static final String PASSWORD = "admin123";
    private static final String USERNAME = "admin";
    private static final String TENANT = "admin";
    private static final String TEST_CONTROLLER_IP = "10.3.241.221";

    @Inject
    BundleContext context;

    @Inject
    SdnControllerApi api;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private SdnRedirectionApi redirApi;

    private static final VirtualizationConnectorElement VC =
            new VirtualizationConnectorElement() {

                @Override
                public String getName() {
                    return "dummy";
                }

                @Override
                public String getControllerIpAddress() {
                    return "dummy";                }

                @Override
                public String getControllerUsername() {
                    return "dummy";                }

                @Override
                public String getControllerPassword() {
                    return "dummy";                }

                @Override
                public boolean isControllerHttps() {
                    return false;
                }

                @Override
                public String getProviderIpAddress() {
                    return TEST_CONTROLLER_IP;       }

                @Override
                public String getProviderUsername() {
                    return USERNAME;                }

                @Override
                public String getProviderPassword() {
                    return PASSWORD;                }

                @Override
                public String getProviderAdminTenantName() {
                    return TENANT;                }

                @Override
                public String getProviderAdminDomainId() {
                    return DOMAIN_NAME;                }

                @Override
                public boolean isProviderHttps() {
                    return false;
                }

                @Override
                public Map<String, String> getProviderAttributes() {
                    return null;
                }

                @Override
                public SSLContext getSslContext() {
                    return null;
                }

                @Override
                public TrustManager[] getTruststoreManager() throws Exception {
                    return null;
                }
    };

    @org.ops4j.pax.exam.Configuration
    public Option[] config() {

        try {
            return options(

                    // Load the current module from its built classes so we get
                    // the latest from Eclipse
                    bundle("reference:file:" + PathUtils.getBaseDir() + "/target/classes/"),
                    // And some dependencies

                    mavenBundle("com.fasterxml.jackson.core", "jackson-databind").versionAsInProject(),
                    mavenBundle("com.fasterxml.jackson.core", "jackson-annotations").versionAsInProject(),
                    mavenBundle("com.fasterxml.jackson.core", "jackson-core").versionAsInProject(),
                    mavenBundle("com.fasterxml.jackson.jaxrs", "jackson-jaxrs-base").versionAsInProject(),
                    mavenBundle("com.fasterxml.jackson.jaxrs", "jackson-jaxrs-json-provider").versionAsInProject(),
                    mavenBundle("org.osc.plugin", "nsfc-uber-openstack4j").versionAsInProject(),

                    mavenBundle("org.glassfish.jersey.core", "jersey-client").versionAsInProject(),
                    mavenBundle("org.glassfish.jersey.core", "jersey-common").versionAsInProject(),
                    mavenBundle("org.glassfish.jersey.bundles.repackaged", "jersey-guava").versionAsInProject(),
                    mavenBundle("org.glassfish.hk2", "hk2-api").versionAsInProject(),
                    mavenBundle("org.glassfish.hk2", "hk2-locator").versionAsInProject(),
                    mavenBundle("org.glassfish.hk2", "hk2-utils").versionAsInProject(),
                    mavenBundle("org.glassfish.hk2", "osgi-resource-locator").versionAsInProject(),
                    mavenBundle("javax.annotation", "javax.annotation-api").versionAsInProject(),
                    mavenBundle("org.glassfish.hk2.external", "aopalliance-repackaged").versionAsInProject(),
                    mavenBundle("javax.ws.rs", "javax.ws.rs-api").versionAsInProject(),
                    mavenBundle("org.glassfish.jersey.media", "jersey-media-json-jackson").versionAsInProject(),

                    mavenBundle("org.apache.felix", "org.apache.felix.scr").versionAsInProject(),

                    mavenBundle("org.osc.api", "sdn-controller-api").versionAsInProject(),

                    mavenBundle("org.osgi", "org.osgi.core").versionAsInProject(),

                    // Hibernate
                    systemPackage("javax.naming"), systemPackage("javax.annotation"),
                    systemPackage("javax.xml.stream;version=1.0"), systemPackage("javax.xml.stream.events;version=1.0"),
                    systemPackage("javax.xml.stream.util;version=1.0"), systemPackage("javax.transaction;version=1.1"),
                    systemPackage("javax.transaction.xa;version=1.1"),

                    mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.antlr")
                            .versionAsInProject(),
                    mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.dom4j")
                            .versionAsInProject(),
                    mavenBundle("org.javassist", "javassist").versionAsInProject(),
//                    mavenBundle("org.jboss.logging", "jboss-logging").versionAsInProject(),
//                    mavenBundle("org.jboss", "jandex").versionAsInProject(),

                    mavenBundle("com.fasterxml", "classmate").versionAsInProject(),
                    mavenBundle("org.javassist", "javassist").versionAsInProject(),

                    mavenBundle("org.slf4j", "slf4j-api").versionAsInProject(),
                    mavenBundle("ch.qos.logback", "logback-core").versionAsInProject(),
                    mavenBundle("ch.qos.logback", "logback-classic").versionAsInProject(),

                    mavenBundle("org.apache.directory.studio", "org.apache.commons.lang").versionAsInProject(),

                    // Uncomment this line to allow remote debugging
//                    CoreOptions.vmOption("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1047"),

                    bootClasspathLibrary(mavenBundle("org.apache.geronimo.specs", "geronimo-jta_1.1_spec", "1.1.1"))
                            .beforeFramework(),
                    junitBundles());
        } catch (Throwable t) {

            System.err.println(t.getClass().getName() + ":\n" + t.getMessage());
            t.printStackTrace(System.err);
            throw t;
        }
    }

    @After
    public void tearDown() throws Exception {
        if (this.redirApi != null) {
            this.redirApi.close();
        }
    }

    @Test
    public void dummy() {

    }

    public void verifyApiResponds() throws Exception {
        // Act.
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");
        InspectionHookElement noSuchHook = this.redirApi.getInspectionHook("No shuch hook");

        // Assert.
        assertNull(noSuchHook);
        assertTrue(this.redirApi instanceof NeutronSfcSdnRedirectionApi);
    }
}
