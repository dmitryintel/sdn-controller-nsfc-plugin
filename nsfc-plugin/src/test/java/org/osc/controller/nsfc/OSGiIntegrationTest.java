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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.api.client.IOSClientBuilder.V3;
import org.openstack4j.api.exceptions.ClientResponseException;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.openstack4j.openstack.OSFactory;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.util.PathUtils;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.controller.nsfc.entities.NetworkElementImpl;
import org.osc.controller.nsfc.entities.PortPairElement;
import org.osc.controller.nsfc.entities.ServiceFunctionChainElement;
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
    private static final String TEST_CONTROLLER_IP = "10.3.243.183";

    private static final String INGRESS0_ID = "ec1ca134-db9c-4fdc-8964-2b18851eca1b";
    private static final String EGRESS0_ID = "ec1ca134-db9c-4fdc-8964-2b18851eca1b";
//    private static final String EGRESS0_ID = "5686a891-cf7a-4803-a286-e00c70a96995";

    private static final String INGRESS0_IP = "172.16.3.14";
    private static final String EGRESS0_IP = "172.16.3.14";
//    private static final String EGRESS0_IP = "172.16.1.4";

    private static final String INGRESS0_MAC = "fa:16:3e:cb:44:2d";
    private static final String EGRESS0_MAC = "fa:16:3e:cb:44:2d";
//    private static final String EGRESS0_MAC = "fa:16:3e:66:5b:42";

    private static final String INGRESS1_ID = "6c47e906-f305-4ba0-9b7f-2b375c166b0d";
    private static final String EGRESS1_ID = "6c47e906-f305-4ba0-9b7f-2b375c166b0d";

    private static final String INGRESS1_IP = "172.16.1.5";
    private static final String EGRESS1_IP = "172.16.1.5";

    private static final String INGRESS1_MAC = "fa:16:3e:d3:84:7e";
    private static final String EGRESS1_MAC = "fa:16:3e:d3:84:7e";

    private static final String INSPECTED_ID = "11e5dd85-b1f7-422d-a822-181351b22aef";
    private static final String INSPECTED_IP = "172.16.3.8";
    private static final String INSPECTED_MAC = "fa:16:3e:ca:37:38";

    // just for verifying stuff
    private OSClientV3 osClient;
    private NetworkElementImpl ingressElement0;
    private NetworkElementImpl egressElement0;
    private PortPairElement inspectionPortElement0;
    private NetworkElementImpl ingressElement1;
    private NetworkElementImpl egressElement1;
    private PortPairElement inspectionPortElement1;

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

        // On control node, use 'drivers = dummy' in /etc/neutron/neutron.conf. That's under [ovs] section.
        // You should have prepared an opentack with sfc and two servers on management network!
        // Normally, we want to use 'drivers = sfc' but that one is buggy as of 3/15/18.
        // See https://docs.openstack.org/networking-sfc/latest/install/index.html

        this.ingressElement0 = new NetworkElementImpl(INGRESS0_ID, asList(INGRESS0_MAC),
                                                                    asList(INGRESS0_IP), null);
        this.egressElement0 = new NetworkElementImpl(EGRESS0_ID, asList(EGRESS0_MAC),
                                                                  asList(EGRESS0_IP), null);
        this.inspectionPortElement0 = new PortPairElement(null, null, this.ingressElement0, this.egressElement0);

        this.ingressElement1 = new NetworkElementImpl(INGRESS1_ID, asList(INGRESS1_MAC),
                                                                    asList(INGRESS1_IP), null);
        this.egressElement1 = new NetworkElementImpl(EGRESS1_ID, asList(EGRESS1_MAC),
                       asList(EGRESS1_IP), null);
        this.inspectionPortElement1 = new PortPairElement(null, null, this.ingressElement1, this.egressElement1);
    }

    @After
    public void tearDown() throws Exception {
        if (this.redirApi != null) {
            this.redirApi.close();
        }
//        cleanAllOnOpenstack();
    }

//  @Test
    public void verifyApiResponds() throws Exception {
        // Act.
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");
        InspectionHookElement noSuchHook = this.redirApi.getInspectionHook("No shuch hook");

        // Assert.
        assertNull(noSuchHook);
        assertTrue(this.redirApi instanceof NeutronSfcSdnRedirectionApi);
    }

    // The following test proves that create calls are not idempotent.
    // It results in ClientResponseException {message=Port Pair
    // with ingress port <SOME_UUID> and
    // egress port <SOME_UUID> is already used by another Port Pair
    // <SOME_UUID>, status=400, status-code=BAD_REQUEST}
    //    @Test
    public void testIdempotent() throws Exception {

        PortPair pp = Builders.portPair().ingressId(INGRESS0_ID).egressId(EGRESS0_ID).build();

        pp = this.osClient.sfc().portpairs().create(pp);

        pp = pp.toBuilder().id(null).projectId(null).build();
        this.exception.expect(ClientResponseException.class);
        pp = this.osClient.sfc().portpairs().create(pp);
    }

//    @Test
    public void testIdempotentPPG() throws Exception {

        PortPair pp = Builders.portPair().ingressId(INGRESS0_ID).egressId(EGRESS0_ID).build();

        pp = this.osClient.sfc().portpairs().create(pp);

        PortPairGroup ppg = Builders.portPairGroup().portPairs(Arrays.asList(pp.getId())).build();
        ppg = this.osClient.sfc().portpairgroups().create(ppg);
        ppg = ppg.toBuilder().id(null).projectId(null).build();

        this.exception.expect(ClientResponseException.class);
        this.osClient.sfc().portpairgroups().create(ppg);
    }

//    @Test
    public void testPortPairsWorkflow() throws Exception {
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");

        // TEST CALL
        Element result0 = this.redirApi.registerInspectionPort(this.inspectionPortElement0);

        assertNotNull(result0);
        LOG.debug("Success registering inspection port {} (Actual class {})", result0.getElementId(), result0.getClass());
        this.inspectionPortElement0 = (PortPairElement) result0;

        assertPortPairGroupIsOk(this.inspectionPortElement0);
        assertIngressEgressOk(this.inspectionPortElement0);

        // same parent
        this.inspectionPortElement1.setPortPairGroup(this.inspectionPortElement0.getPortPairGroup());

        // TEST CALL
        Element result1 = this.redirApi.registerInspectionPort(this.inspectionPortElement1);

        assertNotNull(result1);
        LOG.debug("Success registering inspection port {} (Actual class {})", result1.getElementId(), result1.getClass());
        this.inspectionPortElement1 = (PortPairElement) result1;

        assertEquals(this.inspectionPortElement0.getParentId(), this.inspectionPortElement1.getParentId());
        assertPortPairGroupIsOk(this.inspectionPortElement1);
        assertIngressEgressOk(this.inspectionPortElement0);

        // TEST CALL
        this.redirApi.removeInspectionPort(this.inspectionPortElement0);

        assertNull(this.osClient.sfc().portpairs().get(this.inspectionPortElement0.getElementId()));
        assertNotNull(this.osClient.sfc().portpairs().get(this.inspectionPortElement1.getElementId()));
        assertNotNull(this.osClient.sfc().portpairgroups().get(this.inspectionPortElement0.getParentId()));

        // TEST CALL
        this.redirApi.removeInspectionPort(this.inspectionPortElement1);

        assertNull(this.osClient.sfc().portpairs().get(this.inspectionPortElement1.getElementId()));
        assertNull(this.osClient.sfc().portpairgroups().get(this.inspectionPortElement1.getParentId()));
    }

//    @Test
    public void testInspectionHooksWorkflow_BothPairsInSamePPG() throws Exception {
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");

        Element result0 = this.redirApi.registerInspectionPort(this.inspectionPortElement0);
        this.inspectionPortElement0 = (PortPairElement) result0;

        // same parent
        this.inspectionPortElement1.setPortPairGroup(this.inspectionPortElement0.getPortPairGroup());

        Element result1 = this.redirApi.registerInspectionPort(this.inspectionPortElement1);
        this.inspectionPortElement1 = (PortPairElement) result1;

        // TEST CALL
        NetworkElement ne = this.redirApi.registerNetworkElement(asList(this.inspectionPortElement0.getPortPairGroup()));
        ServiceFunctionChainElement sfc = (ServiceFunctionChainElement) ne;

        NetworkElementImpl inspected = new NetworkElementImpl(INSPECTED_ID, asList(INSPECTED_MAC),
                                                                  asList(INSPECTED_IP), null);

        // TEST CALL
        String hookId = this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);

        assertNotNull(hookId);

        // TEST CALL
        InspectionHookElement ih = this.redirApi.getInspectionHook(hookId);

        assertNotNull(ih);

        Port inspectedPortCheck = this.osClient.networking().port().get(INSPECTED_ID);
        assertNotNull(inspectedPortCheck);

        String sfcId = ih.getInspectionPort().getElementId();
        assertEquals(sfc.getElementId(), sfcId);

        ServiceFunctionChainElement sfcElementCheck = (ServiceFunctionChainElement) ih.getInspectionPort();
        assertEquals(sfc.getElementId(), sfcElementCheck.getElementId());
        assertNotNull(sfcElementCheck.getInspectionHooks());
        assertEquals(1, sfcElementCheck.getInspectionHooks().size());
        assertEquals(hookId, sfcElementCheck.getInspectionHooks().iterator().next().getHookId());

        PortChain portChainCheck = this.osClient.sfc().portchains().get(sfcId);
        assertNotNull(portChainCheck);

        FlowClassifier flowClassifierCheck = this.osClient.sfc().flowclassifiers().get(hookId);
        assertNotNull(flowClassifierCheck);
        assertTrue(portChainCheck.getFlowClassifiers().contains(hookId));

        // TEST CALL
        this.redirApi.removeInspectionHook(hookId);

        assertNull(this.redirApi.getInspectionHook(hookId));
        assertNull(this.osClient.sfc().flowclassifiers().get(hookId));

        portChainCheck = this.osClient.sfc().portchains().get(sfcId);
        assertFalse(portChainCheck.getFlowClassifiers() != null
                          && portChainCheck.getFlowClassifiers().contains(hookId));

        // TEST CALL
        this.redirApi.deleteNetworkElement(sfc);
        assertNull(this.osClient.sfc().portchains().get(sfc.getElementId()));
    }

//    @Test
    public void cleanAllOnOpenstack() {
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

    private void assertIngressEgressOk(PortPairElement inspectionPortElement) {
        PortPair portPairCheck = this.osClient.sfc().portpairs().get(inspectionPortElement.getElementId());
        assertEquals(inspectionPortElement.getEgressPort().getElementId(), portPairCheck.getEgressId());
        assertEquals(inspectionPortElement.getEgressPort().getElementId(), portPairCheck.getEgressId());
    }

    private void assertPortPairGroupIsOk(PortPairElement inspectionPortElement) {
        assertNotNull(inspectionPortElement.getPortPairGroup());
        PortPairGroup ppgCheck = this.osClient.sfc().portpairgroups().get(inspectionPortElement.getParentId());
        assertNotNull(ppgCheck);
        assertNotNull(ppgCheck.getPortPairs());
        assertTrue(ppgCheck.getPortPairs().contains(inspectionPortElement.getElementId()));
    }
}
