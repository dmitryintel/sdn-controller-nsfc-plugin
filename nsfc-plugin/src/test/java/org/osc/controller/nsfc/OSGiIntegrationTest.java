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

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.osc.sdk.controller.FailurePolicyType.NA;
import static org.osc.sdk.controller.TagEncapsulationType.VLAN;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.api.client.IOSClientBuilder.V3;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.openstack4j.openstack.OSFactory;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.util.PathUtils;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.NetworkElementEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.sdk.controller.api.SdnControllerApi;
import org.osc.sdk.controller.api.SdnRedirectionApi;
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.NetworkElement;
import org.osc.sdk.controller.element.VirtualizationConnectorElement;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@RunWith(PaxExam.class)
//@ExamReactorStrategy(PerClass.class)
public class OSGiIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(OSGiIntegrationTest.class);

    private static final String DOMAIN_NAME = "default";
    private static final String PASSWORD = "admin123";
    private static final String USERNAME = "admin";
    private static final String TENANT = "admin";
    private static final String TEST_CONTROLLER_IP = "10.3.241.221";

    private static final String INGRESS0_ID = "969ed382-022e-4477-bcaf-ef92666a3641";
    private static final String EGRESS0_ID = "49db7011-34d5-4ea7-96e2-6549fbd3df66";

    private static final String INGRESS0_IP = "192.168.1.66";
    private static final String EGRESS0_IP = "172.16.0.13";

    private static final String INGRESS0_MAC = "fa:16:3e:63:3c:bf";
    private static final String EGRESS0_MAC = "fa:16:3e:63:3c:bf";

    private static final String INGRESS1_ID = "c3a45f6e-4390-4183-86a9-a201f5e799f7";
    private static final String EGRESS1_ID = "f5e75b90-6b81-41dc-a1d4-189c4e8bb875";

    private static final String INGRESS1_IP = "192.168.1.13";
    private static final String EGRESS1_IP = "172.16.0.11";

    private static final String INGRESS1_MAC = "fa:16:3e:72:83:ab";
    private static final String EGRESS1_MAC = "fa:16:3e:59:23:35";

    private static final String INSPECTED_ID = "5db6a898-956f-424f-8371-abcf7a20aa03";
    private static final String INSPECTED_IP = "172.16.0.3";
    private static final String INSPECTED_MAC = "fa:16:3e:72:83:ab";

    // just for verifying stuff
    private OSClientV3 osClient;
    private NetworkElementEntity ingressEntity0;
    private NetworkElementEntity egressEntity0;
    private InspectionPortEntity inspectionPortEntity0;
    private NetworkElementEntity ingressEntity1;
    private NetworkElementEntity egressEntity1;
    private InspectionPortEntity inspectionPortEntity1;

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

                    mavenBundle("com.fasterxml", "classmate").versionAsInProject(),
                    mavenBundle("org.slf4j", "slf4j-api").versionAsInProject(),
                    mavenBundle("ch.qos.logback", "logback-core").versionAsInProject(),
                    mavenBundle("ch.qos.logback", "logback-classic").versionAsInProject(),

                    mavenBundle("org.apache.directory.studio", "org.apache.commons.lang").versionAsInProject(),
                    mavenBundle("com.google.guava","guava").versionAsInProject(),
                    // Uncomment this line to allow remote debugging
                    // CoreOptions.vmOption("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1047"),

                    bootClasspathLibrary(mavenBundle("org.apache.geronimo.specs", "geronimo-jta_1.1_spec", "1.1.1"))
                            .beforeFramework(),
                    junitBundles());
        } catch (Throwable t) {
            System.err.println(t.getClass().getName() + ":\n" + t.getMessage());
            t.printStackTrace(System.err);
            throw t;
        }
    }

    @Before
    public void setup() {
        String domain = VC.getProviderAdminDomainId();
        String username = VC.getProviderUsername();
        String password = VC.getProviderPassword();
        String tenantName = VC.getProviderAdminTenantName();

        V3 v3 = OSFactory.builderV3()
                .endpoint("http://" + VC.getProviderIpAddress() + ":5000/v3")
                .credentials(username, password, Identifier.byName(domain))
                .scopeToProject(Identifier.byName(tenantName), Identifier.byName(domain));

        this.osClient = v3.authenticate();

        LOG.debug("You should have prepared an opentack with sfc and two servers with two ports each!");

        this.ingressEntity0 = new NetworkElementEntity(INGRESS0_ID, asList(INGRESS0_MAC),
                                                                    asList(INGRESS0_IP), null);
        this.egressEntity0 = new NetworkElementEntity(EGRESS0_ID, asList(EGRESS0_MAC),
                                                                  asList(EGRESS0_IP), null);
        this.inspectionPortEntity0 = new InspectionPortEntity(null, null, this.ingressEntity0, this.egressEntity0);

        this.ingressEntity1 = new NetworkElementEntity(INGRESS1_ID, asList(INGRESS1_MAC),
                                                                    asList(INGRESS1_IP), null);
        this.egressEntity1 = new NetworkElementEntity(EGRESS1_ID, asList(EGRESS1_MAC),
                       asList(EGRESS1_IP), null);
        this.inspectionPortEntity1 = new InspectionPortEntity(null, null, this.ingressEntity1, this.egressEntity1);
    }

    @After
    public void tearDown() throws Exception {
        if (this.redirApi != null) {
            this.redirApi.close();
        }
    }

    public void verifyApiResponds() throws Exception {
        // Act.
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");
        InspectionHookElement noSuchHook = this.redirApi.getInspectionHook("No shuch hook");

        // Assert.
        assertNull(noSuchHook);
        assertTrue(this.redirApi instanceof NeutronSfcSdnRedirectionApi);
    }

    public void testPortPairsWorkflow() throws Exception {
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");
        Element result0 = this.redirApi.registerInspectionPort(this.inspectionPortEntity0);

        assertNotNull(result0);
        LOG.debug("Success registering inspection port {} (Actual class {})", result0.getElementId(), result0.getClass());
        this.inspectionPortEntity0 = (InspectionPortEntity) result0;

        assertPortPairGroupIsOk(this.inspectionPortEntity0);
        assertIngressEgressOk(this.inspectionPortEntity0);

        // same parent
        this.inspectionPortEntity1.setPortPairGroup(this.inspectionPortEntity0.getPortPairGroup());

        Element result1 = this.redirApi.registerInspectionPort(this.inspectionPortEntity1);
        assertNotNull(result1);
        LOG.debug("Success registering inspection port {} (Actual class {})", result1.getElementId(), result1.getClass());
        this.inspectionPortEntity1 = (InspectionPortEntity) result1;

        assertEquals(this.inspectionPortEntity0.getParentId(), this.inspectionPortEntity1.getParentId());
        assertPortPairGroupIsOk(this.inspectionPortEntity1);
        assertIngressEgressOk(this.inspectionPortEntity0);

        this.redirApi.removeInspectionPort(this.inspectionPortEntity0);
        assertNull(this.osClient.sfc().portpairs().get(this.inspectionPortEntity0.getElementId()));
        assertNotNull(this.osClient.sfc().portpairs().get(this.inspectionPortEntity1.getElementId()));
        assertNotNull(this.osClient.sfc().portpairgroups().get(this.inspectionPortEntity0.getParentId()));

        this.redirApi.removeInspectionPort(this.inspectionPortEntity1);
        assertNull(this.osClient.sfc().portpairs().get(this.inspectionPortEntity1.getElementId()));
        assertNull(this.osClient.sfc().portpairgroups().get(this.inspectionPortEntity1.getParentId()));
    }

    public void testInspectionHooksWorkflow_BothPairsInSamePPG() throws Exception {
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");

        Element result0 = this.redirApi.registerInspectionPort(this.inspectionPortEntity0);
        this.inspectionPortEntity0 = (InspectionPortEntity) result0;

        // same parent
        this.inspectionPortEntity1.setPortPairGroup(this.inspectionPortEntity0.getPortPairGroup());

        Element result1 = this.redirApi.registerInspectionPort(this.inspectionPortEntity1);
        this.inspectionPortEntity1 = (InspectionPortEntity) result1;

        NetworkElement ne = this.redirApi.registerNetworkElement(asList(this.inspectionPortEntity0.getPortPairGroup()));
        ServiceFunctionChainEntity sfc = (ServiceFunctionChainEntity) ne;

        NetworkElementEntity inspected = new NetworkElementEntity(INSPECTED_ID, asList(INSPECTED_MAC),
                                                                  asList(INSPECTED_IP), null);

        String hookId = this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);
        assertNotNull(hookId);

        InspectionHookElement ih = this.redirApi.getInspectionHook(hookId);
        assertNotNull(ih);
        assertNotNull(ih.getInspectionPort());
        String sfcId = ih.getInspectionPort().getElementId();

        PortChain portChainCheck = this.osClient.sfc().portchains().get(sfcId);
        FlowClassifier flowClassifierCheck = this.osClient.sfc().flowclassifiers().get(hookId);
        assertNotNull(portChainCheck);
        assertNotNull(flowClassifierCheck);
        assertTrue(portChainCheck.getFlowClassifiers().contains(hookId));

    }

    public void cleanPortPairsPPGsAndChains() {
        List<? extends PortChain> portChains = this.osClient.sfc().portchains().list();
        List<? extends FlowClassifier> flowClassifiers = this.osClient.sfc().flowclassifiers().list();
        List<? extends PortPairGroup> portPairGroups = this.osClient.sfc().portpairgroups().list();
        List<? extends PortPair> portPairs = this.osClient.sfc().portpairs().list();

        for (PortChain pc : portChains) {
            this.osClient.sfc().portchains().delete(pc.getId());
        }
        for (FlowClassifier fc : flowClassifiers) {
            this.osClient.sfc().flowclassifiers().delete(fc.getId());
        }
        for (PortPairGroup ppg : portPairGroups) {
            this.osClient.sfc().portpairgroups().delete(ppg.getId());
        }
        for (PortPair pp : portPairs) {
            this.osClient.sfc().portpairs().delete(pp.getId());
        }

        assertEquals("Failed clean port chains!", 0, this.osClient.sfc().portchains().list().size());
        assertEquals("Failed clean flow classifiers!", 0, this.osClient.sfc().flowclassifiers().list().size());
        assertEquals("Failed clean port pair groups!", 0, this.osClient.sfc().portpairgroups().list().size());
        assertEquals("Failed clean port pairs!", 0, this.osClient.sfc().portpairs().list().size());
    }

    private void assertIngressEgressOk(InspectionPortEntity inspectionPortEntity) {
        PortPair portPairCheck = this.osClient.sfc().portpairs().get(inspectionPortEntity.getElementId());
        assertEquals(inspectionPortEntity.getEgressPort().getElementId(), portPairCheck.getEgressId());
        assertEquals(inspectionPortEntity.getEgressPort().getElementId(), portPairCheck.getEgressId());
    }

    private void assertPortPairGroupIsOk(InspectionPortEntity inspectionPortEntity) {
        assertNotNull(inspectionPortEntity.getPortPairGroup());
        PortPairGroup ppgCheck = this.osClient.sfc().portpairgroups().get(inspectionPortEntity.getParentId());
        assertNotNull(ppgCheck);
        assertNotNull(ppgCheck.getPortPairs());
        assertTrue(ppgCheck.getPortPairs().contains(inspectionPortEntity.getElementId()));
    }
}
