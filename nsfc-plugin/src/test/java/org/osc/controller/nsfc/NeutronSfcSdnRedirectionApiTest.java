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

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.PortPairGroupEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.sdk.controller.DefaultNetworkPort;
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

    public void testApi_RegisterInspectionPort_Succeeds() throws Exception {
    }

    public void testApi_RegisterInspectionPortWithNetworkElementsAlreadyPersisted_Succeeds() throws Exception {
    }

    public void testApi_RegisterInspectionPortWithParentId_Succeeds() throws Exception {
    }

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

    public void testApi_RemoveSingleInspectionPort_VerifyPPGDeleted() throws Exception {
    }

    public void testApi_RemoveSingleInspectionPort_VerifyPPGNotDeleted() throws Exception {
    }

    // Inspection hooks test

    public void testApi_InstallInspectionHook_VerifySucceeds() throws Exception {
    }

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

    public void testApi_InstallInspectionHook_WithExistingHook_VerifyFails() throws Exception {
    }

    public void testApi_UpdateInspectionHook_WithExistingHook_VerifySucceeds() throws Exception {
    }

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

    public void testApi_RemoveInspectionHookById_InspectionHookDisappears() throws Exception {
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
        ne.setElementId(portPairGroup.getId());
        neList.add(ne);

        this.exception.expect(IllegalArgumentException.class);
        this.exception
                .expectMessage(String.format(String.format("Port Pair Group Id %s is already chained to SFC Id : %s ",
                        ne.getElementId(), portChain.getId())));

        // Act
        this.redirApi.registerNetworkElement(neList);
    }

    @Test
    public void testApi_RegisterNetworkElement_VerifySuccess() throws Exception {
        // Arrange
        persistInspectionPort();
        List<NetworkElement> neList = new ArrayList<NetworkElement>();
        DefaultNetworkPort ne = new DefaultNetworkPort();
        ne.setElementId(portPairGroup.getId());
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

        sfcTest.setElementId(portChain.getId());

        ne.setElementId("BadPpgId");
        ne.setParentId(portChain.getId());
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
        persistInspectionPortAndSfc();

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

    private void persistInspectionPortAndSfc() {
        persistInspectionPort();
        portChain = Builders.portChain()
                .portPairGroups(singletonList(portPairGroup.getId()))
                .build();
        portChain = portChainService.create(portChain);
    }
}
