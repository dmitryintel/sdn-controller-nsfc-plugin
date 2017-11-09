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
import static org.osc.controller.nsfc.TestData.*;
import static org.osc.sdk.controller.FailurePolicyType.NA;
import static org.osc.sdk.controller.TagEncapsulationType.VLAN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.core.StringStartsWith;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.NetworkElementEntity;
import org.osc.controller.nsfc.entities.PortPairGroupEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.controller.nsfc.utils.RedirectionApiUtils;
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
    public void setup() {
        super.setup();
        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);
    }

    // Inspection port tests

    @Test
    public void testApi_RegisterInspectionPort_Succeeds() throws Exception {
        // Arrange.
        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, null, ingress, egress);

        // Act.
        inspectionPortElement = (InspectionPortElement) this.redirApi.registerInspectionPort(inspectionPortElement);

        //Assert.

        // Here we are mostly afraid of LazyInitializationException
        assertNotNull(inspectionPortElement.getElementId());
        assertNotNull(inspectionPortElement.getParentId());
        assertNotNull(inspectionPortElement.getIngressPort());
        assertNotNull(inspectionPortElement.getEgressPort());
        assertNotNull(inspectionPortElement.getEgressPort().getMacAddresses());
        assertNotNull(inspectionPortElement.getEgressPort().getElementId());
        assertNotNull(inspectionPortElement.getIngressPort());
        assertNotNull(inspectionPortElement.getIngressPort().getMacAddresses());
        assertNotNull(inspectionPortElement.getIngressPort().getElementId());
        inspectionPortElement.getIngressPort().getParentId();
        inspectionPortElement.getEgressPort().getParentId();

        final InspectionPortElement inspectionPortElementTmp = inspectionPortElement;
        NetworkElementEntity foundIngress = this.txControl.required(() -> {
            return this.em.find(NetworkElementEntity.class, inspectionPortElementTmp.getIngressPort().getElementId());
        });

        assertNotNull(foundIngress);
        assertEquals(inspectionPortElement.getIngressPort().getElementId(), foundIngress.getElementId());

        // Here we are afraid of lazyInitializationException
        foundIngress.getMacAddresses();
        foundIngress.getPortIPs();
        foundIngress.getElementId();
        foundIngress.getParentId();

        InspectionPortElement foundInspPortElement = this.redirApi.getInspectionPort(inspectionPortElement);
        assertEquals(inspectionPortElement.getIngressPort().getElementId(),
                foundInspPortElement.getIngressPort().getElementId());
        assertEquals(inspectionPortElement.getEgressPort().getElementId(),
                foundInspPortElement.getEgressPort().getElementId());
        assertEquals(inspectionPortElement.getElementId(), foundInspPortElement.getElementId());

        assertEquals(foundInspPortElement.getParentId(), inspectionPortElement.getParentId());
    }

    @Test
    public void testApi_RegisterInspectionPortWithNetworkElementsAlreadyPersisted_Succeeds() throws Exception {
        // Arrange.
        this.txControl.required(() -> {
            this.em.persist(ingress);
            this.em.persist(egress);
            return null;
        });

        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, null, ingress, egress);

        // Act.
        inspectionPortElement = (InspectionPortElement) this.redirApi.registerInspectionPort(inspectionPortElement);

        // Assert.
        assertNotNull(inspectionPortElement);
        assertNotNull(inspectionPortElement.getElementId());
        assertNotNull(inspectionPortElement.getParentId());
        assertNotNull(inspectionPortElement.getIngressPort());
        assertNotNull(inspectionPortElement.getEgressPort());
        assertEquals(ingress.getElementId(), inspectionPortElement.getIngressPort().getElementId());
        assertEquals(egress.getElementId(), inspectionPortElement.getEgressPort().getElementId());
    }

    @Test
    public void testApi_RegisterInspectionPortWithParentId_Succeeds() throws Exception {
        // Arrange.
        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, null, ingress, egress);
        Element result = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertNotNull(result.getParentId());
        String portGroupId = result.getParentId();

        RedirectionApiUtils utils = new RedirectionApiUtils(this.em, this.txControl);

        ppg = utils.findByPortPairgroupId(portGroupId);
        InspectionPortElement inspectionPortElement2 = new InspectionPortEntity(null, ppg,
                new NetworkElementEntity("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementEntity("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        // Act.
        Element result2 = this.redirApi.registerInspectionPort(inspectionPortElement2);

        // Assert.
        assertEquals(portGroupId, result2.getParentId());
    }

    @Test
    public void testApi_RegisterInspectionPortWithInvalidParentId_Fails() throws Exception {
        // Arrange.
        this.exception.expect(IllegalArgumentException.class);

        ppg = new PortPairGroupEntity();
        ppg.setElementId("fooportgroup");

        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, ppg, ingress,
                egress);

        // Act.
        this.redirApi.registerInspectionPort(inspectionPortElement);
    }

    @Test
    public void testApi_RemoveSingleInspectionPort_VerifyPPGDeleted() throws Exception {
        // Arrange.
        InspectionPortEntity inspectionPortElement = new InspectionPortEntity(null, null, ingress, egress);

        Element registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertTrue(registeredElement instanceof InspectionPortEntity);
        String elementId = registeredElement.getElementId();

        InspectionPortEntity foundInspectionPort = this.txControl.required(() -> {
            InspectionPortEntity tmpInspectionPort = this.em.find(InspectionPortEntity.class, elementId);
            assertNotNull(tmpInspectionPort);
            return tmpInspectionPort;
        });

        assertEquals(elementId, foundInspectionPort.getElementId());
        String ppgId = foundInspectionPort.getParentId();
        String ingressPortId = foundInspectionPort.getIngressPort().getElementId();
        String egressPortId = foundInspectionPort.getEgressPort().getElementId();

        // Act.
        this.redirApi.removeInspectionPort(inspectionPortElement);

        // Assert.
        this.txControl.required(() -> {

            assertNull(this.em.find(InspectionPortEntity.class, elementId));
            assertNull(this.em.find(PortPairGroupEntity.class, ppgId));
            assertNull(this.em.find(NetworkElementEntity.class, ingressPortId));
            assertNull(this.em.find(NetworkElementEntity.class, egressPortId));

            return null;
        });
    }

    @Test
    public void testApi_RemoveSingleInspectionPort_VerifyPPGNotDeleted() throws Exception {
        // Arrange.
        InspectionPortEntity inspectionPortElement = new InspectionPortEntity(null, null, ingress, egress);

        Element registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertTrue(registeredElement instanceof InspectionPortEntity);

        String elementId = registeredElement.getElementId();

        InspectionPortEntity foundInspectionPort = this.txControl.required(() -> {
            InspectionPortEntity tmpInspectionPort = this.em.find(InspectionPortEntity.class, elementId);
            assertNotNull(tmpInspectionPort);
            return tmpInspectionPort;
        });

        assertEquals(elementId, foundInspectionPort.getElementId());

        InspectionPortElement inspectionPortElement2 = new InspectionPortEntity(null,
                foundInspectionPort.getPortPairGroup(),
                new NetworkElementEntity("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementEntity("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement2);

        String ppgId = foundInspectionPort.getParentId();

        // Act.
        this.redirApi.removeInspectionPort(inspectionPortElement);

        // Assert.
        foundInspectionPort = this.txControl.required(() -> {
            return this.em.find(InspectionPortEntity.class, elementId);
        });

        ppg = this.txControl.required(() -> {
            return this.em.find(PortPairGroupEntity.class, ppgId);
        });

        assertNull(foundInspectionPort);
        assertNotNull(ppg);
    }

    // Inspection hooks test

    @Test
    public void testApi_InstallInspectionHook_VerifySucceeds() throws Exception {

        // Arrange.
        persistInspectionPort();
        this.txControl.required(() -> {

            sfc.getPortPairGroups().add(ppg);
            this.em.persist(sfc);

            return null;
        });

        // Act.
        final String hookId = this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L,
                NA);

        // Assert.
        assertNotNull(hookId);

        InspectionHookElement inspectionHookElement = this.txControl.required(() -> {
            InspectionHookEntity tmp = this.em.find(InspectionHookEntity.class, hookId);
            assertNotNull(tmp);
            assertEquals(tmp.getServiceFunctionChain().getElementId(), sfc.getElementId());
            return tmp;
        });

        // Here we are mostly afraid of LazyInitializationException
        assertNotNull(inspectionHookElement);
        assertNotNull(inspectionHookElement.getHookId());
        assertEquals(inspectionHookElement.getInspectedPort().getElementId(), inspected.getElementId());
    }

    @Test
    public void testApi_InstallInspectionHook_WithNoInspectedPort_VerifyFails() throws Exception {

        // Arrange
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage("null passed for Inspection port !");

        this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L,
                NA);

        // Inspected port with non-existing id
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(StringStartsWith.startsWith("Cannot find type Service Function Chain"));

        // Act.
        this.redirApi.installInspectionHook(inspected, new ServiceFunctionChainEntity("foo"), 0L, VLAN, 0L,
                NA);
    }

    @Test
    public void testApi_InstallInspectionHook_WithExistingHook_VerifyFails() throws Exception {
        // Arrange.
        persistInspectionPort();
        this.txControl.required(() -> {

            sfc.getPortPairGroups().add(ppg);
            this.em.persist(sfc);

            return null;
        });

        this.exception.expect(IllegalStateException.class);
        this.exception.expectMessage(StringStartsWith.startsWith("Found existing inspection hook"));

        this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);

        // Act.
        this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);
    }

    @Test
    public void testApi_UpdateInspectionHook_WithExistingHook_VerifySucceeds() throws Exception {
        // Arrange.
        persistInspectionHook();

        String hookId = inspectionHook.getHookId();

        // Setup new SFC
        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null,
                null,
                new NetworkElementEntity("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementEntity("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        Element portPairEntity = this.redirApi.registerInspectionPort(inspectionPortElement);

        ServiceFunctionChainEntity newSfc = this.txControl.required(() -> {
            ServiceFunctionChainEntity tmpSfc = new ServiceFunctionChainEntity();
            tmpSfc.getPortPairGroups().add(new PortPairGroupEntity(portPairEntity.getParentId()));
            this.em.persist(tmpSfc);
            return tmpSfc;
        });

        InspectionHookEntity updatedHook = new InspectionHookEntity(inspected, newSfc);
        updatedHook.setHookId(hookId);

        // Act.
        this.redirApi.updateInspectionHook(updatedHook);

        this.txControl.required(() -> {
            InspectionHookEntity tmp = this.em.find(InspectionHookEntity.class, hookId);
            assertNotNull(tmp);
            assertEquals(tmp.getServiceFunctionChain().getElementId(), newSfc.getElementId());
            return null;
        });
    }

    @Test
    public void testApi_UpdateInspectionHook_WithMissingHook_VerifyFailure() throws Exception {
        // Arrange.
        persistInspectionPortAndSfc();

        InspectionHookEntity updatedHook = new InspectionHookEntity(inspected, sfc);
        updatedHook.setHookId("non-existing-id");

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(StringStartsWith.startsWith("Cannot find Inspection Hook"));

        // Act
        this.redirApi.updateInspectionHook(updatedHook);
    }

    @Test
    public void testApi_RemoveInspectionHookById_InspectionHookDisappears() throws Exception {
        // Arrange.
        persistInspectionHook();

        InspectionHookEntity inspectionHookEntity = this.txControl.required(() -> {
            InspectionHookEntity tmpInspectionHook = this.em.find(InspectionHookEntity.class, inspectionHook.getHookId());
            return tmpInspectionHook;
        });

        assertNotNull(inspectionHookEntity);

        // Act.
        this.redirApi.removeInspectionHook(inspectionHookEntity.getHookId());

        // Assert.
        inspectionHookEntity = this.txControl.required(() -> {
            InspectionHookEntity tmpInspectionHook = this.em.find(InspectionHookEntity.class, inspectionHook.getHookId());
            return tmpInspectionHook;
        });

        NetworkElementEntity inspectedPortNetworkElement = this.txControl.required(() -> {
            return this.em.find(NetworkElementEntity.class, inspected.getElementId());
        });

        assertNull(inspectionHookEntity);
        assertNull(inspectedPortNetworkElement);
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
    public void testApi_RegisterNetworkElementWithPpgIdNull_ThrowsIllegalArgumentException() throws Exception {
        // Arrange

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        neList.add(ne);

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !",  "Port Pair Group Id"));

        // Act
        this.redirApi.registerNetworkElement(neList);
    }

    @Test
    public void testApi_RegisterNetworkElementWithInvalidPpgId_ThrowsIllegalArgumentException() throws Exception {
        // Arrange

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        ne.setElementId("badId");
        neList.add(ne);

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("Cannot find %s by id: %s!", "Port Pair Group", ne.getElementId()));

        // Act
        this.redirApi.registerNetworkElement(neList);
    }

    @Test
    public void testApi_RegisterNetworkElementWithPpgIdIsChainedToAnotherSfc_ThrowsIllegalArgumentException()
            throws Exception {
        // Arrange
        persistInspectionPortAndSfc();
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        ne.setElementId(ppg.getElementId());
        neList.add(ne);

        this.exception.expect(IllegalArgumentException.class);
        this.exception
                .expectMessage(String.format(String.format("Port Pair Group Id %s is already chained to SFC Id : %s ",
                        ne.getElementId(), ppg.getServiceFunctionChain().getElementId())));

        // Act
        this.redirApi.registerNetworkElement(neList);
    }

    @Test
    public void testApi_RegisterNetworkElement_VerifySuccess() throws Exception {
        // Arrange
        persistInspectionPort();
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        ne.setElementId(ppg.getElementId());
        neList.add(ne);

        // Act
        NetworkElement neResponse=  this.redirApi.registerNetworkElement(neList);

        // Assert
        this.txControl.required(() -> {
            sfc = this.em.find(ServiceFunctionChainEntity.class, neResponse.getElementId());
            assertNotNull("SFC is not to be found after creation", sfc);
            return null;
        });
    }

    @Test
    public void testApi_UpdateNetworkElementWithNullSfc_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(
                String.format(String.format("null passed for %s !", "Port Pair Group Service Function Chain Id")));

        // Act
        this.redirApi.updateNetworkElement(null, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWithSfcIdNull_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("null passed for %s !", "Port Pair Group Service Function Chain Id"));

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
        persistInspectionPortAndSfc();

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
                return ((String)s).matches(".*null.+Port Pair Group Service Function Chain Id.*");
            }

            @Override
            public void describeTo(Description description) {
            }});

        // Act
        this.redirApi.updateNetworkElement(sfcPort, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWhenPpgIdInUpdatedListIsNotFound_ThrowsIllegalArgumentException()
            throws Exception {
        // Arrange
        persistInspectionPortAndSfc();

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort sfcTest = new DefaultNetworkPort();
        DefaultNetworkPort ne = new DefaultNetworkPort();

        sfcTest.setElementId(sfc.getElementId());

        ne.setElementId("BadPpgId");
        ne.setParentId(sfc.getElementId());
        neList.add(ne);

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(String.format("Cannot find %s by id: %s!", "Port Pair Group", ne.getElementId()));

        // Act
        this.redirApi.updateNetworkElement(sfcTest, neList);
    }

    @Test
    public void testApi_UpdateNetworkElementWhenPpgIdIsChainedToSameSfc_VerifySuccessful()
            throws Exception {
        // Arrange
        persistInspectionPortAndSfc();

        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        DefaultNetworkPort sfcTest = new DefaultNetworkPort();

        sfcTest.setElementId(sfc.getElementId());

        ne.setElementId(ppg.getElementId());
        neList.add(ne);

        // Act
        NetworkElement sfcReturn = this.redirApi.updateNetworkElement(sfcTest, neList);
        Assert.assertEquals("Return Sfc is not equal tosfc", sfc.getElementId(), sfcReturn.getElementId());
    }

    @Test
    public void testApi_UpdateNetworkElement_VerifySuccessful() throws Exception {
        // Arrange
        List<PortPairGroupEntity> ppgList = persistNInspectionPort(4);
        ServiceFunctionChainEntity sfcPersist = persistNppgsInSfc(ppgList);

        List<NetworkElement> neReverseList = new ArrayList<NetworkElement>();
        DefaultNetworkPort sfcTest = new DefaultNetworkPort();

        sfcTest.setElementId(sfcPersist.getElementId());

        Collections.reverse(ppgList);
        List<String> ppgListSrc = new ArrayList<String>();
        for(PortPairGroupEntity ppg_local : ppgList) {
            DefaultNetworkPort ne = new DefaultNetworkPort();
            ne.setElementId(ppg_local.getElementId());
            ne.setParentId(sfcPersist.getElementId());
            neReverseList.add(ne);
            ppgListSrc.add(ppg_local.getElementId());
        }

        // Act
        NetworkElement neResponse = this.redirApi.updateNetworkElement(sfcTest, neReverseList);
        this.txControl.required(() -> {
            ServiceFunctionChainEntity sfcTarget = this.em.find(ServiceFunctionChainEntity.class, neResponse.getElementId());
            assertNotNull("SFC is not to be found after creation", sfcTarget);
            List<String> ppgListTarget = new ArrayList<String>();
            for(PortPairGroupEntity ppg_local : sfcTarget.getPortPairGroups()) {
                ppgListTarget.add(ppg_local.getElementId());
            }
            Assert.assertEquals("The list of port pair group ids is different than expected", ppgListSrc, ppgListTarget);
            return null;
        });
    }

    @Test
    public void testApi_DeleteNetworkElementWhenSfcToDeleteIsNotFound_ThrowsIllegalArgumentException() throws Exception {
        // Arrange
        DefaultNetworkPort ne = new DefaultNetworkPort();

        ne.setElementId("bad-id");

        this.exception.expect(IllegalArgumentException.class);
        this.exception
                .expectMessage(String.format("Cannot find %s by id: %s!", "Service Function Chain", ne.getElementId()));

        // Act
        this.redirApi.deleteNetworkElement(ne);
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
        persistInspectionPortAndSfc();
        String localSfcId = sfc.getElementId();

        DefaultNetworkPort ne = new DefaultNetworkPort();

        ne.setElementId(sfc.getElementId());

        // Act
        this.redirApi.deleteNetworkElement(ne);
        this.txControl.required(() -> {
            ServiceFunctionChainEntity sfc_t = this.em.find(ServiceFunctionChainEntity.class, localSfcId);
            assertNull("SFC still exist after deletion", sfc_t);
            return null;
        });
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
        persistInspectionPortAndSfc();

        DefaultNetworkPort ne = new DefaultNetworkPort();

        ne.setElementId(sfc.getElementId());

        // Act
        List<NetworkElement> neResponseList = this.redirApi.getNetworkElements(ne);

        // Assert.
        assertNotNull("SFC chain List is Empty", neResponseList);
    }

    private List<PortPairGroupEntity> persistNInspectionPort(int count) {
        List<PortPairGroupEntity> ppgList = new ArrayList<PortPairGroupEntity>();
        for(int i=0;i<count;i++) {
            InspectionPortEntity insp = new InspectionPortEntity();
            PortPairGroupEntity ppg_n= new PortPairGroupEntity();

            this.txControl.required(() -> {
                    this.em.persist(ppg_n);

                    insp.setPortPairGroup(ppg_n);
                    this.em.persist(insp);

                    ppg_n.getPortPairs().add(insp);
                    this.em.merge(ppg_n);
                    return null;
            });
            ppgList.add(ppg_n);
        }
        return ppgList;
    }

    private ServiceFunctionChainEntity persistNppgsInSfc(List<PortPairGroupEntity> ppgList) {

        return this.txControl.required(() -> {
            for(PortPairGroupEntity ppgCurr : ppgList) {
                sfc.getPortPairGroups().add(ppgCurr);
                this.em.persist(sfc);

                ppgCurr.setServiceFunctionChain(sfc);
                this.em.merge(ppgCurr);
            }

            return sfc;
        });
    }

    private ServiceFunctionChainEntity persistInspectionPortAndSfc() {
        persistInspectionPort();
        return this.txControl.required(() -> {
            sfc.getPortPairGroups().add(ppg);
            this.em.persist(sfc);

            ppg.setServiceFunctionChain(sfc);
            this.em.merge(ppg);
            return sfc;
        });
    }
}
