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
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;
import static org.osc.controller.nsfc.TestData.*;
import static org.osc.sdk.controller.FailurePolicyType.NA;
import static org.osc.sdk.controller.TagEncapsulationType.VLAN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.core.StringStartsWith;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.controller.nsfc.entities.FlowClassifierElement;
import org.osc.controller.nsfc.entities.NetworkElementImpl;
import org.osc.controller.nsfc.entities.PortPairElement;
import org.osc.controller.nsfc.entities.PortPairGroupElement;
import org.osc.controller.nsfc.entities.ServiceFunctionChainElement;
import org.osc.sdk.controller.DefaultNetworkPort;
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.InspectionPortElement;
import org.osc.sdk.controller.element.NetworkElement;

@RunWith(MockitoJUnitRunner.class)
public class NeutronSfcSdnRedirectionApiTest extends AbstractNeutronSfcPluginTest {

    private NeutronSfcSdnRedirectionApi redirApi;

    @Before
    @Override
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setup();
        this.redirApi = new NeutronSfcSdnRedirectionApi(this.osClient);
    }

    // Inspection port tests
    @Test
    public void testApi_RegisterInspectionPort_PortPairNonexistent_Succeeds() throws Exception {
        // Arrange.

        //Ingress and egress must be already persisted!
        persistIngress();
        persistEgress();

        InspectionPortElement inspectionPortElement = new PortPairElement(null, null, ingressPortElement, egressPortElement);

        // Act.
        inspectionPortElement = (InspectionPortElement) this.redirApi.registerInspectionPort(inspectionPortElement);

        //Assert.
        assertNotNull(inspectionPortElement.getElementId());
        assertNotNull(inspectionPortElement.getParentId());

        InspectionPortElement foundInspPortElement = this.redirApi.getInspectionPort(inspectionPortElement);
        assertEquals(inspectionPortElement.getElementId(), foundInspPortElement.getElementId());
    }

    @Test
    public void testApi_RegisterInspectionPort_PortPairAlreadyExists_Succeeds() throws Exception {
        // Arrange.

        //Ingress and egress must be already persisted!
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);

        InspectionPortElement inspectionPortElement = new PortPairElement(portPair.getId(), null, ingressPortElement, egressPortElement);

        // Act.
        inspectionPortElement = (InspectionPortElement) this.redirApi.registerInspectionPort(inspectionPortElement);

        //Assert.
        assertNotNull(inspectionPortElement.getElementId());
        assertNotNull(inspectionPortElement.getParentId());

        InspectionPortElement foundInspPortElement = this.redirApi.getInspectionPort(inspectionPortElement);
        assertNotNull(foundInspPortElement);
    }

    @Test
    public void testApi_RegisterInspectionPortWithParentId_Succeeds() throws Exception {
        // Arrange.
        persistIngress();
        persistEgress();

        InspectionPortElement inspectionPortElement = new PortPairElement(null, null, ingressPortElement, egressPortElement);
        Element result = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertNotNull(result.getParentId());
        String portPairGroupId = result.getParentId();

        ppgElement = new PortPairGroupElement(portPairGroupId);

        InspectionPortElement inspectionPortElement2 = new PortPairElement(null, ppgElement,
                new NetworkElementImpl("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementImpl("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        // Act.
        Element result2 = this.redirApi.registerInspectionPort(inspectionPortElement2);

        // Assert.
        assertEquals(portPairGroupId, result2.getParentId());
        PortPairGroup portPairGroup = this.osClient.sfc().portpairgroups().get(portPairGroupId);
        assertNotNull(portPairGroup);
        assertEquals(portPairGroupId, portPairGroup.getId());
        assertEquals(2, portPairGroup.getPortPairs().size());
        assertTrue(portPairGroup.getPortPairs().contains(result.getElementId()));
        assertTrue(portPairGroup.getPortPairs().contains(result2.getElementId()));
    }

    @Test
    public void testApi_RegisterInspectionPortWithInvalidParentId_Fails() throws Exception {
        // Arrange.
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();
        this.exception.expect(IllegalArgumentException.class);

        ppgElement = new PortPairGroupElement();
        ppgElement.setElementId("fooportgroup");

        InspectionPortElement inspectionPortElement = new PortPairElement(null, ppgElement, ingressPortElement,
                egressPortElement);

        // Act.
        this.redirApi.registerInspectionPort(inspectionPortElement);
    }

    @Test
    public void testApi_RemoveSingleInspectionPort_VerifyPPGDeleted() throws Exception {
        // Arrange.
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        // port pair group should be created on its own!
        assertEquals(0, this.osClient.sfc().portpairgroups().list().size());

        PortPairElement inspectionPortElement = new PortPairElement(null, null, ingressPortElement, egressPortElement);

        Element registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertTrue(registeredElement instanceof PortPairElement);
        inspectionPortElement = (PortPairElement) registeredElement;

        portPair = this.osClient.sfc().portpairs().get(inspectionPortElement.getElementId());
        assertNotNull(portPair);

        assertEquals(1, this.osClient.sfc().portpairgroups().list().size());
        portPairGroup = this.osClient.sfc().portpairgroups().list().get(0);
        assertTrue(portPairGroup.getPortPairs().contains(portPair.getId()));
        assertEquals(portPairGroup.getId(), registeredElement.getParentId());

        this.redirApi.getInspectionPort(new PortPairElement(portPair.getId(), null, ingressPortElement, egressPortElement));

        assertEquals(inspectionPortElement.getElementId(), portPair.getId());

        // Act.
        this.redirApi.removeInspectionPort(inspectionPortElement);

        // Assert.
        assertNull(this.osClient.sfc().portpairs().get(inspectionPortElement.getElementId()));

        // Ports themselves are not deleted. Only the pair
        // assertNull(this.osClient.networking().port().get(ingressPortId));
    }

    @Test
    public void testApi_RemoveSingleInspectionPort_VerifyPPGNotDeleted() throws Exception {
        // Arrange.
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        // port pair group should be created on its own!
        assertEquals(0, this.osClient.sfc().portpairgroups().list().size());

        PortPairElement inspectionPortElement = new PortPairElement(null, null, ingressPortElement, egressPortElement);
        Element registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertTrue(registeredElement instanceof PortPairElement);
        inspectionPortElement = (PortPairElement) registeredElement;

        portPair = this.osClient.sfc().portpairs().get(inspectionPortElement.getElementId());
        assertNotNull(portPair);

        assertEquals(1, this.osClient.sfc().portpairgroups().list().size());
        portPairGroup = this.osClient.sfc().portpairgroups().list().get(0);
        assertNotNull(portPairGroup);
        assertEquals(1, portPairGroup.getPortPairs().size());
        assertTrue(portPairGroup.getPortPairs().contains(portPair.getId()));

        ppgElement = new PortPairGroupElement(portPairGroup.getId());
        InspectionPortElement inspectionPortElement2 = new PortPairElement(null,
                ppgElement,
                new NetworkElementImpl("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementImpl("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        Element registeredElement2 = this.redirApi.registerInspectionPort(inspectionPortElement2);
        inspectionPortElement2 = (PortPairElement) registeredElement2;

        portPairGroup = this.osClient.sfc().portpairgroups().get(portPairGroup.getId());
        assertNotNull(portPairGroup);
        assertEquals(2, portPairGroup.getPortPairs().size());
        assertTrue(portPairGroup.getPortPairs().contains(portPair.getId()));
        assertTrue(portPairGroup.getPortPairs().contains(inspectionPortElement2.getElementId()));

        // Act.
        this.redirApi.removeInspectionPort(inspectionPortElement);

        // Assert.
        portPair = this.osClient.sfc().portpairs().get(inspectionPortElement.getElementId());
        assertNull(portPair);
        portPairGroup = this.osClient.sfc().portpairgroups().get(portPairGroup.getId());
        assertNotNull(portPairGroup);
        assertFalse(portPairGroup.getPortPairs().contains(inspectionPortElement.getElementId()));

        assertEquals(1, portPairGroup.getPortPairs().size());
        assertTrue(portPairGroup.getPortPairs().contains(inspectionPortElement2.getElementId()));
    }

    // Inspection hooks tests
    @Test
    public void testApi_InstallInspectionHook_VerifySucceeds() throws Exception {
        // Arrange.
        persistInspectedPort();
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();
        persistPortChainAndSfcElement();

        // Act.
        final String hookId = this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);

        // Assert.
        assertNotNull(hookId);

        FlowClassifier flowClassifier = this.osClient.sfc().flowclassifiers().get(hookId);
        assertNotNull(flowClassifier);

        InspectionHookElement inspectionHookElement = this.redirApi.getInspectionHook(hookId);
        assertTrue(inspectionHookElement instanceof FlowClassifierElement);
        inspectionHook= (FlowClassifierElement) inspectionHookElement;
        assertNotNull(inspectionHook);
        assertEquals(hookId, inspectionHook.getHookId());
        assertNotNull(inspectionHook.getServiceFunctionChain());
        assertEquals(sfc.getElementId(), inspectionHook.getServiceFunctionChain().getElementId());
    }

    @Test
    public void testApi_InstallInspectionHook_WithNoInspectedPort_VerifyFails() throws Exception {

        // Arrange
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage("null passed for Service Function Chain !");

        this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);

        // Inspected port with non-existing id
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(StringStartsWith.startsWith("Cannot find type Service Function Chain"));

        // Act.
        this.redirApi.installInspectionHook(inspected, new ServiceFunctionChainElement("foo"), 0L, VLAN, 0L, NA);
    }

    @Test
    public void testApi_UpdateInspectionHook_WithExistingHook_VerifySucceeds() throws Exception {
        // Arrange.
        persistInspectedPort();

        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();
        persistPortChainAndSfcElement();

        String hookId = this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);
        assertNotNull(hookId);

        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();
        persistPortChainAndSfcElement();
        ServiceFunctionChainElement sfcOther = sfc; // sfc has been renewed

        FlowClassifierElement inspectionHookAlt = new FlowClassifierElement(hookId, inspected, sfcOther);

        // Act.
        this.redirApi.updateInspectionHook(inspectionHookAlt);

        InspectionHookElement updatedHook = this.redirApi.getInspectionHook(hookId);

        assertNotNull(updatedHook);
        assertEquals(sfcOther.getElementId(), updatedHook.getInspectionPort().getElementId());
    }

    @Test
    public void testApi_UpdateInspectionHook_WithMissingHook_VerifyFailure() throws Exception {
        // Arrange.
        persistPortChainAndSfcElement();

        FlowClassifierElement updatedHook = new FlowClassifierElement("non-existing-id", inspected, sfc);

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(StringStartsWith.startsWith("Cannot find Flow Classifier"));

        // Act
        this.redirApi.updateInspectionHook(updatedHook);
    }

    @Test
    public void testApi_RemoveInspectionHookById_InspectionHookDisappears() throws Exception {
        // Arrange.
        persistInspectedPort();
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();
        persistPortChainAndSfcElement();

        String hookId = this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);
        assertNotNull(this.redirApi.getInspectionHook(hookId));

        // Act.
        this.redirApi.removeInspectionHook(hookId);

        // Assert.
        assertNull(this.redirApi.getInspectionHook(hookId));
    }

    @Test
    public void testApi_RegisterNetworkElementWithNullPPGList_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Port Pair Group member list"));

        // Act
        this.redirApi.registerNetworkElement(null);
    }

    @Test
    public void testApi_RegisterNetworkElementWithEmptyPPGList_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        List<NetworkElement> neList = new ArrayList<NetworkElement>();

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Port Pair Group member list"));

        // Act
        this.redirApi.registerNetworkElement(neList);
    }

    @Test
    public void testApi_RegisterNetworkElement_VerifySuccess() throws Exception {
        // Arrange
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        PortPairGroupElement ne = new PortPairGroupElement(portPairGroup.getId());
        neList.add(ne);

        // Act
        NetworkElement neResponse=  this.redirApi.registerNetworkElement(neList);

        assertNotNull(portChainService.get(neResponse.getElementId()));
    }

    @Test
    public void testApi_UpdateNetworkElementWithNullSfc_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(
                String.format(String.format("null passed for %s !", "Service Function Chain Id")));

        // Act
        this.redirApi.updateNetworkElement(null, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWithSfcIdNull_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Service Function Chain Id"));

        // Act
        this.redirApi.updateNetworkElement(ne, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWithNullUpdatedPpgList_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        DefaultNetworkPort ne = new DefaultNetworkPort();
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Port Pair Group update member list"));
        ne.setElementId("goodid");

        // Act
        this.redirApi.updateNetworkElement(ne, null);
    }

    @Test
    public void testApi_UpdateNetworkElementWithEmptyUpdatedPpgList_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Port Pair Group update member list"));
        ne.setElementId("goodid");

        // Act
        this.redirApi.updateNetworkElement(ne, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWhenSfcToUpdateIsNotFound_ThrowsIllegalArgumentException() throws Exception {
        // Arrange

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();

        ne.setElementId("bad-id");
        neList.add(ne);

        this.exception.expect(IllegalArgumentException.class);
        this.exception
                .expectMessage(String.format("Cannot find %s by id: %s!", "Service Function Chain", ne.getElementId()));

        // Act
        this.redirApi.updateNetworkElement(ne, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWhenPpgIdIsNullInUpdatedList_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        persistPortChainAndSfcElement();

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort sfcPort = new DefaultNetworkPort();
        DefaultNetworkPort ne = new DefaultNetworkPort();

        sfcPort.setElementId(sfcPort.getElementId());

        ne.setParentId("BadId");
        neList.add(ne);

        this.exception.expect(IllegalArgumentException.class);

        this.exception.expectMessage(new BaseMatcher<String>() {

            @Override
            public boolean matches(Object s) {
                if (!(s instanceof String)) {
                    return false;
                }
                return ((String)s).matches(".*null.+Service Function Chain Id.*");
            }

            @Override
            public void describeTo(Description description) {
            }});

        // Act
        this.redirApi.updateNetworkElement(sfcPort, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWhenPpgIdIsChainedToSameSfc_VerifySuccessful()
            throws Exception {
        // Arrange
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();
        persistPortChainAndSfcElement();

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        DefaultNetworkPort sfcTest = new DefaultNetworkPort();

        sfcTest.setElementId(portChain.getId());

        ne.setElementId(portPairGroup.getId());
        neList.add(ne);

        // Act
        NetworkElement sfcReturn = this.redirApi.updateNetworkElement(sfcTest, neList);
        Assert.assertEquals("Return Sfc is not equal tosfc", portChain.getId(), sfcReturn.getElementId());
    }

    @Test
    public void testApi_UpdateNetworkElement_VerifySuccessful() throws Exception {
        // Arrange
        List<PortPairGroup> ppgList = persistNInspectionPort(4);
        PortChain pChain = Builders.portChain()
                .portPairGroups(ppgList.stream().map(PortPairGroup::getId).collect(toList()))
                .build();
        pChain = portChainService.create(pChain);

        List<NetworkElement> neReverseList = new ArrayList<NetworkElement>();
        DefaultNetworkPort sfcTest = new DefaultNetworkPort();

        sfcTest.setElementId(pChain.getId());

        Collections.reverse(ppgList);
        List<String> ppgListSrc = new ArrayList<String>();
        for(PortPairGroup ppg_local : ppgList) {
            DefaultNetworkPort ne = new DefaultNetworkPort();
            ne.setElementId(ppg_local.getId());
            ne.setParentId(pChain.getId());
            neReverseList.add(ne);
            ppgListSrc.add(ppg_local.getId());
        }

        // Act
        NetworkElement neResponse = this.redirApi.updateNetworkElement(sfcTest, neReverseList);

        PortChain sfcTarget = portChainService.get(neResponse.getElementId());
        assertNotNull("SFC is not to be found after creation", sfcTarget);
        List<String> ppgListTarget = new ArrayList<String>();
        for(String ppgId : sfcTarget.getPortPairGroups()) {
            ppgListTarget.add(ppgId);
        }
        Assert.assertEquals("The list of port pair group ids is different than expected", ppgListSrc, ppgListTarget);
    }

    @Test
    public void testApi_DeleteNetworkElementWhenSfcElementIsNull_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Service Function Chain Id"));

        // Act
        this.redirApi.deleteNetworkElement(null);
    }

    @Test
    public void testApi_DeleteNetworkElementWhenSfcIdIsNull_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        DefaultNetworkPort ne = new DefaultNetworkPort();

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Service Function Chain Id"));

        // Act
        this.redirApi.deleteNetworkElement(ne);
    }

    @Test
    public void testApi_DeleteNetworkElement_VerifySuccessful() throws Exception {
        // Arrange
        persistPortChainAndSfcElement();
        String localSfcId = portChain.getId();

        DefaultNetworkPort ne = new DefaultNetworkPort();

        ne.setElementId(portChain.getId());

        // Act
        this.redirApi.deleteNetworkElement(ne);

        assertNull(portChainService.get(localSfcId));
    }

    @Test
    public void testApi_GetNetworkElementWhenSfcElementIsNull_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Service Function Chain Id"));

        // Act
        this.redirApi.getNetworkElements(null);
    }

    @Test
    public void testApi_GetNetworkElementWhenSfcIdIsNull_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        DefaultNetworkPort ne = new DefaultNetworkPort();

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Service Function Chain Id"));

        // Act
        this.redirApi.getNetworkElements(ne);
    }

    @Test
    public void testApi_GetNetworkElementWhenSfcGetIsNotFound_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        DefaultNetworkPort ne = new DefaultNetworkPort();

        ne.setElementId("bad-id");

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("Cannot find %s by id: %s!", "Service Function Chain", ne.getElementId()));

        // Act
        this.redirApi.getNetworkElements(ne);
    }

    @Test
    public void testApi_GetNetworkElement_VerifySuccessful() throws Exception {
        // Arrange
        persistIngress();
        persistEgress();
        persistInspectionPort(true, true);
        persistPortPairGroup();
        persistPortChainAndSfcElement();

        DefaultNetworkPort ne = new DefaultNetworkPort();

        ne.setElementId(portChain.getId());

        // Act
        List<NetworkElement> neResponseList = this.redirApi.getNetworkElements(ne);

        // Assert.
        assertNotNull("SFC chain List is Empty", neResponseList);
    }

    private List<PortPairGroup> persistNInspectionPort(int count) {
        List<PortPairGroup> ppgList = new ArrayList<>();
        for(int i=0;i<count;i++) {
            PortPair inspPort_n = Builders.portPair().build();
            inspPort_n = portPairService.create(inspPort_n);
            PortPairGroup ppg_n= Builders.portPairGroup()
                                    .portPairs(singletonList(inspPort_n.getId())).build();
            ppg_n = portPairGroupService.create(ppg_n);
            ppgList.add(ppg_n);
        }
        return ppgList;
    }

    private void persistInspectedPort() {
        Port inspectedPort = portService.create(Builders.port().macAddress(inspected.getMacAddresses().get(0))
                .fixedIp(inspected.getPortIPs().get(0), "mySubnet").build());

        inspected = constructNetworkElementElement(inspectedPort, null);
    }

    private ServiceFunctionChainElement persistPortChainAndSfcElement() {

        portChain = Builders.portChain()
                .portPairGroups(singletonList(portPairGroup.getId()))
                .flowClassifiers(new ArrayList<>())
                .build();

        portChain = portChainService.create(portChain);

        ingressPortElement = constructNetworkElementElement(ingressPort, portPair.getId());
        egressPortElement = constructNetworkElementElement(egressPort, portPair.getId());
        inspectionPort.setIngressPort(ingressPortElement);
        inspectionPort.setEgressPort(egressPortElement);
        ppgElement.getPortPairs().add(inspectionPort);
        ppgElement.setElementId(portPairGroup.getId());

        sfc = new ServiceFunctionChainElement(portChain.getId());
        sfc.getPortPairGroups().add(ppgElement);
        ppgElement.setServiceFunctionChain(sfc);

        return sfc;
    }

    private NetworkElementImpl constructNetworkElementElement(Port port, String parentId) {
        List<String> ips;
        if (port.getFixedIps() != null) {
            ips = port.getFixedIps().stream().map(ip -> ip.getIpAddress()).collect(Collectors.toList());
        } else {
            ips = new ArrayList<>();
        }
        return new NetworkElementImpl(port.getId(), singletonList(port.getMacAddress()), ips, parentId);
    }
}
