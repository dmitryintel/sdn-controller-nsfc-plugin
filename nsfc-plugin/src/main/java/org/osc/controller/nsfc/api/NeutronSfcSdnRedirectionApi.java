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
package org.osc.controller.nsfc.api;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.NetworkElementEntity;
import org.osc.controller.nsfc.entities.PortPairGroupEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.controller.nsfc.utils.RedirectionApiUtils;
import org.osc.sdk.controller.FailurePolicyType;
import org.osc.sdk.controller.TagEncapsulationType;
import org.osc.sdk.controller.api.SdnRedirectionApi;
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.InspectionPortElement;
import org.osc.sdk.controller.element.NetworkElement;
import org.osc.sdk.controller.exception.NetworkPortNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronSfcSdnRedirectionApi implements SdnRedirectionApi {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronSfcSdnRedirectionApi.class);

    private RedirectionApiUtils utils;
    private OSClientV3 osClient;

    public NeutronSfcSdnRedirectionApi() {
    }

    public NeutronSfcSdnRedirectionApi(OSClientV3 osClient) {
        this.utils = new RedirectionApiUtils(osClient);
        this.osClient = osClient;
    }

    // Inspection port methods
    @Override
    public InspectionPortElement getInspectionPort(InspectionPortElement inspectionPort) throws Exception {
        if (inspectionPort == null) {
            LOG.warn("Attempt to find null InspectionPort");
            return null;
        }

        return null; // TODO (Dmitry)
    }

    @Override
    public Element registerInspectionPort(InspectionPortElement inspectionPort) throws Exception {
        if (inspectionPort == null) {
            throw new IllegalArgumentException("Attempt to register null InspectionPort");
        }
        return null; // TODO (Dmitry)
    }

    @Override
    public void removeInspectionPort(InspectionPortElement inspectionPort)
            throws NetworkPortNotFoundException, Exception {
        if (inspectionPort == null) {
            LOG.warn("Attempt to remove a null Inspection Port");
            return;
        }
        // TODO (Dmitry)
    }

    // Inspection Hooks methods
    @Override
    public String installInspectionHook(NetworkElement inspectedPort, InspectionPortElement inspectionPort, Long tag,
            TagEncapsulationType encType, Long order, FailurePolicyType failurePolicyType)
            throws NetworkPortNotFoundException, Exception {

        return null; // TODO (Dmitry)
    }

    @Override
    public void updateInspectionHook(InspectionHookElement providedHook) throws Exception {
        if (providedHook == null || providedHook.getHookId() == null) {
            throw new IllegalArgumentException("Attempt to update a null Inspection Hook!");
        }
        LOG.info(String.format("Updating Inspection Hook %s:", providedHook));
        // TODO (Dmitry)
    }

    @Override
    public void removeInspectionHook(String inspectionHookId) throws Exception {
        if (inspectionHookId == null) {
            LOG.warn("Attempt to remove an Inspection Hook with null id");
            return;
        }

        this.utils.removeSingleInspectionHook(inspectionHookId);
    }

    @Override
    public InspectionHookElement getInspectionHook(String inspectionHookId) throws Exception {
        if (inspectionHookId == null) {
            LOG.warn("Attempt to get Inspection Hook with null id");
            return null;
        }

        return null;  // TODO (Dmitry)
    }

    // SFC methods
    @Override
    public NetworkElement registerNetworkElement(List<NetworkElement> portPairGroupList) throws Exception {
        //check for null or empty list
        this.utils.throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group member list");
        this.utils.validatePPGList(portPairGroupList);

        List<String> portPairGroupIds = portPairGroupList
                                            .stream()
                                            .map(ppg -> ppg.getElementId())
                                            .collect(Collectors.toList());

        PortChain portChain = Builders.portChain()
                                    .description("Port Chain object created by OSC")
                                    .chainParameters(emptyMap())
                                    .flowClassifiers(emptyList())
                                    .portPairGroups(portPairGroupIds)
                                    .build();

        PortChain portChainCreated = this.osClient.sfc().portchains().create(portChain);

        return new NetworkElementEntity(portChainCreated.getId(),
                                        new ArrayList<>(), new ArrayList<>(),
                                        portPairGroupList.get(0).getParentId());
    }

    @Override
    public NetworkElement updateNetworkElement(NetworkElement serviceFunctionChain, List<NetworkElement> portPairGroupList)
            throws Exception {
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Port Pair Group Service Function Chain Id");
        this.utils.throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group update member list");

        PortChain portChain = this.osClient.sfc().portchains().get(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfCannotFindById(portChain, "Service Function Chain", serviceFunctionChain.getElementId());

        portChain = Builders.portChain().from(portChain)
                            .portPairGroups(Collections.emptyList()).build();
        this.osClient.sfc().portchains().update(portChain.getId(), portChain);
        this.utils.validatePPGList(portPairGroupList);

        List<String> portPairGroupIds = portPairGroupList
                .stream()
                .map(ppg -> ppg.getElementId())
                .collect(Collectors.toList());

        portChain = Builders.portChain().from(portChain).portPairGroups(portPairGroupIds).build();
        PortChain portChainUpdated = this.osClient.sfc().portchains().update(portChain.getId(), portChain);
        return new NetworkElementEntity(portChainUpdated.getId(),
                                        new ArrayList<>(), new ArrayList<>(),
                                        portPairGroupList.get(0).getParentId());
    }

    @Override
    public void deleteNetworkElement(NetworkElement serviceFunctionChain) throws Exception {
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Service Function Chain Id");
        PortChain portChain = this.osClient.sfc().portchains().get(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfCannotFindById(portChain,"Service Function Chain", serviceFunctionChain.getElementId());
        ActionResponse response = this.osClient.sfc().portchains().delete(serviceFunctionChain.getElementId());

        if (!response.isSuccess()) {
            throw new Exception("Exception deleting SFC " + serviceFunctionChain.getElementId()
                        + ". Status " + response.getCode() + "\nMessage:\n" + response.getFault());
        }
    }

    @Override
    public List<NetworkElement> getNetworkElements(NetworkElement serviceFunctionChain) throws Exception {
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Service Function Chain Id");

        PortChain portChain = this.osClient.sfc().portchains().get(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfCannotFindById(portChain, "Service Function Chain", serviceFunctionChain.getElementId());

        if (portChain.getPortPairGroups() == null) {
            return emptyList();
        }

        ServiceFunctionChainEntity sfcFound = new ServiceFunctionChainEntity(portChain.getId());
        ArrayList<PortPairGroupEntity> portPairGroupEntities = new ArrayList<>();

        for (String portPairGroupId : portChain.getPortPairGroups()) {
            PortPairGroup portPairGroup = this.osClient.sfc().portpairgroups().get(portPairGroupId);

            if (portPairGroup == null) {
                LOG.error("Port pair group {} not found for port chain {}", portPairGroupId, portChain.getId());
                continue;
            }

            PortPairGroupEntity portPairGroupEntity = new PortPairGroupEntity(portPairGroupId);
            portPairGroupEntity.setServiceFunctionChain(sfcFound);

            for (String portPairId : portPairGroup.getPortPairs()) {
                PortPair portPair = this.osClient.sfc().portpairs().get(portPairId);

                if (portPair == null) {
                    LOG.error("Port pair group {} not found for port pair group {}", portPairId, portPairGroupId);
                    continue;
                }

                NetworkElementEntity ingress = this.utils.retrieveNetworkElementFromOS(portPair.getIngressId(), portPairId);
                NetworkElementEntity egress = this.utils.retrieveNetworkElementFromOS(portPair.getEgressId(), portPairId);

                InspectionPortEntity inspectionPort = new InspectionPortEntity(portPair.getId(), portPairGroupEntity,
                                                                               ingress, egress);
                portPairGroupEntity.getPortPairs().add(inspectionPort);
            }

            portPairGroupEntities.add(portPairGroupEntity);
        }

        sfcFound.setPortPairGroups(portPairGroupEntities);
        return new ArrayList<>(portPairGroupEntities);
    }

    // Unsupported operations in SFC
    @Override
    public InspectionHookElement getInspectionHook(NetworkElement inspectedPort, InspectionPortElement inspectionPort)
            throws Exception {
        throw new UnsupportedOperationException(String.format(
                "Retriving inspection hooks with Inspected port: %s and Inspection port: %s is not supported.",
                inspectedPort, inspectedPort));
    }

    @Override
    public void removeAllInspectionHooks(NetworkElement inspectedPort) throws Exception {
        throw new UnsupportedOperationException("Removing all inspection hooks is not supported in neutron SFC.");
    }

    @Override
    public void removeInspectionHook(NetworkElement inspectedPort, InspectionPortElement inspectionPort)
            throws Exception {
        throw new UnsupportedOperationException(String.format(
                "Removing inspection hooks with Inspected port: %s and Inspection port: %s is not supported.",
                inspectedPort, inspectedPort));
    }

    @Override
    public Long getInspectionHookTag(NetworkElement inspectedPort, InspectionPortElement inspectionPort)
            throws NetworkPortNotFoundException, Exception {
        throw new UnsupportedOperationException("Tags are not supported in neutron SFC.");
    }

    @Override
    public void setInspectionHookTag(NetworkElement inspectedPort, InspectionPortElement inspectionPort, Long tag)
            throws Exception {
        throw new UnsupportedOperationException("Tags are not supported in neutron SFC.");
    }

    @Override
    public FailurePolicyType getInspectionHookFailurePolicy(NetworkElement inspectedPort,
            InspectionPortElement inspectionPort) throws Exception {
        throw new UnsupportedOperationException("Failure policy is not supported in neutron SFC.");
    }

    @Override
    public void setInspectionHookFailurePolicy(NetworkElement inspectedPort, InspectionPortElement inspectionPort,
            FailurePolicyType failurePolicyType) throws Exception {
        throw new UnsupportedOperationException("Failure policy is not supported in neutron SFC.");
    }

    @Override
    public void setInspectionHookOrder(NetworkElement inspectedPort, InspectionPortElement inspectionPort, Long order)
            throws Exception {
        throw new UnsupportedOperationException("Hook order is not supported in neutron SFC.");
    }

    @Override
    public Long getInspectionHookOrder(NetworkElement inspectedPort, InspectionPortElement inspectionPort)
            throws Exception {
        throw new UnsupportedOperationException("Hook order is not supported in neutron SFC.");
    }

    @Override
    public NetworkElement getNetworkElementByDeviceOwnerId(String deviceOwnerId) throws Exception {
        throw new UnsupportedOperationException(
                "Retrieving the network element given the device owner id is currently not supported.");
    }

    @Override
    public void close() throws Exception {
        LOG.info("Closing connection to the database");
    }

}
