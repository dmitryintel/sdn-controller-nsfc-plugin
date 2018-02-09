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
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
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
import org.osgi.service.transaction.control.TransactionControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronSfcSdnRedirectionApi implements SdnRedirectionApi {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronSfcSdnRedirectionApi.class);

    private TransactionControl txControl;
    private EntityManager em;
    private RedirectionApiUtils utils;
    private OSClientV3 osClient;

    public NeutronSfcSdnRedirectionApi() {
    }

    public NeutronSfcSdnRedirectionApi(TransactionControl txControl, EntityManager em, OSClientV3 osClient) {
        this.txControl = txControl;
        this.em = em;
        this.utils = new RedirectionApiUtils(em, txControl, osClient);
        this.osClient = osClient;
    }

    // Inspection port methods
    @Override
    public InspectionPortElement getInspectionPort(InspectionPortElement inspectionPort) throws Exception {
        if (inspectionPort == null) {
            LOG.warn("Attempt to find null InspectionPort");
            return null;
        }

        String portId = inspectionPort.getElementId();

        if (portId != null) {
            try {
                return this.txControl.required(() -> this.utils.txInspectionPortEntityById(portId));
            } catch (Exception e) {
                LOG.warn("Failed to retrieve InspectionPort by id! Trying by ingress and egress " + inspectionPort);
            }
        } else {
            LOG.warn("Failed to retrieve InspectionPort by id! Trying by ingress and egress " + inspectionPort);
        }

        NetworkElement ingress = inspectionPort.getIngressPort();
        NetworkElement egress = inspectionPort.getEgressPort();

        return this.utils.findInspectionPortByNetworkElements(ingress, egress);
    }

    @Override
    public Element registerInspectionPort(InspectionPortElement inspectionPort) throws Exception {
        if (inspectionPort == null) {
            throw new IllegalArgumentException("Attempt to register null InspectionPort");
        }

        String inspectionPortGroupId = inspectionPort.getParentId();
        if (inspectionPortGroupId != null) {
            PortPairGroupEntity ppg = this.utils.findByPortPairgroupId(inspectionPortGroupId);

            this.utils.throwExceptionIfCannotFindById(ppg, "port group", inspectionPortGroupId);
        }

        return this.txControl.required(() -> {

            // must be within this transaction, because if the DB retrievals inside makeInspectionPortEntry
            // are inside the required() call themselves. That makes them a part of a separate transaction

            NetworkElement ingress = inspectionPort.getIngressPort();
            NetworkElement egress = inspectionPort.getEgressPort();
            InspectionPortEntity inspectionPortEntity = this.utils.findInspectionPortByNetworkElements(ingress, egress);

            if (inspectionPortEntity == null) {
                inspectionPortEntity = this.utils.makeInspectionPortEntity(inspectionPort);
            }

            PortPairGroupEntity ppg = inspectionPortEntity.getPortPairGroup();
            if (inspectionPortEntity.getParentId() == null) {
                ppg = new PortPairGroupEntity();
                ppg.getPortPairs().add(inspectionPortEntity);
                this.em.persist(ppg);
                inspectionPortEntity.setPortPairGroup(ppg);
            }

            inspectionPortEntity = this.em.merge(inspectionPortEntity);

            return inspectionPortEntity;
        });
    }

    @Override
    public void removeInspectionPort(InspectionPortElement inspectionPort)
            throws NetworkPortNotFoundException, Exception {
        if (inspectionPort == null) {
            LOG.warn("Attempt to remove a null Inspection Port");
            return;
        }

        InspectionPortElement foundInspectionPort = getInspectionPort(inspectionPort);

        if (foundInspectionPort != null) {
            PortPairGroupEntity ppg = ((InspectionPortEntity) foundInspectionPort).getPortPairGroup();
            this.utils.removeSingleInspectionPort(foundInspectionPort.getElementId());
            ppg.getPortPairs().remove(foundInspectionPort);
            if(ppg.getPortPairs().isEmpty()) {
                this.utils.removePortPairGroup(ppg.getElementId());
            }
        } else {
            NetworkElement ingress = inspectionPort.getIngressPort();
            NetworkElement egress = inspectionPort.getEgressPort();

            LOG.warn(String.format("Attempt to remove nonexistent Inspection Port for ingress %s and egress %s",
                    ingress, egress));
        }
    }

    // Inspection Hooks methods
    @Override
    public String installInspectionHook(NetworkElement inspectedPort, InspectionPortElement inspectionPort, Long tag,
            TagEncapsulationType encType, Long order, FailurePolicyType failurePolicyType)
            throws NetworkPortNotFoundException, Exception {

        this.utils.throwExceptionIfNullElementAndId(inspectedPort, "Inspected port");
        this.utils.throwExceptionIfNullElementAndId(inspectionPort, "Inspection port");

        LOG.info(String.format("Installing Inspection Hook for (Inspected Port %s ; Inspection Port %s):",
                inspectedPort, inspectionPort));

        ServiceFunctionChainEntity sfc = this.utils.findBySfcId(inspectionPort.getElementId());
        this.utils.throwExceptionIfCannotFindById(sfc, "Service Function Chain", inspectionPort.getElementId());

        InspectionHookEntity inspectionHookEntity = this.utils.findInspHookByInspectedAndPort(inspectedPort, sfc);
        if (inspectionHookEntity != null) {
            String msg = String.format("Found existing inspection hook (Inspected %s ; Inspection Port %s)",
                    inspectedPort, inspectionPort);
            LOG.error(msg + " " + inspectionHookEntity);
            throw new IllegalStateException(msg);
        }

        InspectionHookEntity retValEntity = this.txControl.required(() -> {

            InspectionHookEntity createdHookEntity = this.utils.makeInspectionHookEntity(inspectedPort, sfc);
            return this.em.merge(createdHookEntity);
        });

        return retValEntity.getHookId();
    }

    @Override
    public void updateInspectionHook(InspectionHookElement providedHook) throws Exception {
        if (providedHook == null || providedHook.getHookId() == null) {
            throw new IllegalArgumentException("Attempt to update a null Inspection Hook!");
        }
        LOG.info(String.format("Updating Inspection Hook %s:", providedHook));

        NetworkElement providedInspectedPort = providedHook.getInspectedPort();
        InspectionPortElement providedInspectionPort = providedHook.getInspectionPort();

        this.utils.throwExceptionIfNullElementAndId(providedInspectedPort, "Inspected port");
        this.utils.throwExceptionIfNullElementAndId(providedInspectionPort, "Inspection port");

        InspectionHookEntity providedHookEntity = (InspectionHookEntity) getInspectionHook(providedHook.getHookId());
        this.utils.throwExceptionIfCannotFindById(providedHookEntity, "Inspection Hook", providedHook.getHookId());

        NetworkElementEntity providedInspectedPortEntity = providedHookEntity.getInspectedPort();

        if (!providedInspectedPortEntity.getElementId().equals(providedInspectedPort.getElementId())) {
            throw new IllegalStateException(
                    String.format("Cannot update Inspected Port from %s to %s for the Inspection hook %s",
                            providedInspectedPortEntity.getElementId(), providedInspectedPort.getElementId(),
                            providedHookEntity.getHookId()));
        }
        ServiceFunctionChainEntity newSfc = this.utils.findBySfcId(providedInspectionPort.getElementId());
        this.utils.throwExceptionIfCannotFindById(newSfc, "Service Function Chain",
                providedInspectionPort.getElementId());

        this.txControl.required(() -> {
            providedHookEntity.setServiceFunctionChain(newSfc);
            newSfc.getInspectionHooks().add(providedHookEntity);

            this.em.merge(newSfc);
            this.em.merge(providedHookEntity);
            return null;
        });

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

        return this.txControl.required(() -> this.em.find(InspectionHookEntity.class, inspectionHookId));
    }

    // SFC methods
    @Override
    public NetworkElement registerNetworkElement(List<NetworkElement> portPairGroupList) throws Exception {
        // NEW

        //check for null or empty list
        this.utils.throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group member list");

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

        NetworkElementEntity retVal = new NetworkElementEntity(portChainCreated.getId(),
                                            new ArrayList<>(), new ArrayList<>(),
                                            portPairGroupList.get(0).getParentId());

        // OLD

        //check for null or empty list
        this.utils.throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group member list");
        ServiceFunctionChainEntity sfc = new ServiceFunctionChainEntity();

        List<PortPairGroupEntity> ppgList = this.utils.validateAndAdd(portPairGroupList, sfc);

        return this.txControl.required(() -> {
            this.em.persist(sfc);
            for(PortPairGroupEntity ppg : ppgList) {
                this.em.merge(ppg);
            }
            return sfc;
        });

    }

    @Override
    public NetworkElement updateNetworkElement(NetworkElement serviceFunctionChain, List<NetworkElement> portPairGroupList)
            throws Exception {
        // NEW
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Port Pair Group Service Function Chain Id");
        this.utils.throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group update member list");

        PortChain portChain = this.osClient.sfc().portchains().get(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfNull(portChain, PortChain.class);

        List<String> portPairGroupIds = portPairGroupList
                .stream()
                .map(ppg -> ppg.getElementId())
                .collect(Collectors.toList());

        portChain = Builders.portChain().from(portChain).portPairGroups(portPairGroupIds).build();
        PortChain portChainUpdated = this.osClient.sfc().portchains().update(portChain.getId(), portChain);
        NetworkElementEntity retVal = new NetworkElementEntity(portChainUpdated.getId(),
                                                new ArrayList<>(), new ArrayList<>(),
                                                portPairGroupList.get(0).getParentId());

        // OLD
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Port Pair Group Service Function Chain Id");
        this.utils.throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group update member list");

        ServiceFunctionChainEntity sfc = this.utils.findBySfcId(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfCannotFindById(sfc, "Service Function Chain", serviceFunctionChain.getElementId());

        this.utils.validateAndClear(sfc);
        List<PortPairGroupEntity> ppgList = this.utils.validateAndAdd(portPairGroupList, sfc);

        return this.txControl.required(() -> {
            this.em.merge(sfc);
            for(PortPairGroupEntity ppg : ppgList) {
                this.em.merge(ppg);
            }
            return sfc;
        });
    }

    @Override
    public void deleteNetworkElement(NetworkElement serviceFunctionChain) throws Exception {

        // NEW
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Service Function Chain Id");
        PortChain portChain = this.osClient.sfc().portchains().get(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfNull(portChain, PortChain.class);
        ActionResponse response = this.osClient.sfc().portchains().delete(serviceFunctionChain.getElementId());

        if (!response.isSuccess()) {
            throw new Exception("Exception deleting SFC " + serviceFunctionChain.getElementId()
                        + ". Status " + response.getCode() + "\nMessage:\n" + response.getFault());
        }

        // OLD
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Service Function Chain Id");
        ServiceFunctionChainEntity sfc = this.utils.findBySfcId(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfCannotFindById(sfc, "Service Function Chain", serviceFunctionChain.getElementId());

        this.txControl.required(() -> {
            //For delete fetch sfc in a transaction
            ServiceFunctionChainEntity sfcToDel = this.em.find(ServiceFunctionChainEntity.class,
                    serviceFunctionChain.getElementId());

            for (PortPairGroupEntity ppg : sfcToDel.getPortPairGroups()) {
                ppg.setServiceFunctionChain(null);
                this.em.merge(ppg);
            }
            this.em.remove(sfcToDel);
            return null;
        });
    }

    @Override
    public List<NetworkElement> getNetworkElements(NetworkElement serviceFunctionChain) throws Exception {

        // NEW //////////////////////////////////////////
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Service Function Chain Id");

        PortChain portChain = this.osClient.sfc().portchains().get(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfNull(portChain, PortChain.class);

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
        // ... and return
        // return new ArrayList<>(portPairGroupEntities);

        // OLD //////////////////////////////////////////
        this.utils.throwExceptionIfNullElementAndId(serviceFunctionChain, "Service Function Chain Id");
        ServiceFunctionChainEntity sfc = this.utils.findBySfcId(serviceFunctionChain.getElementId());
        this.utils.throwExceptionIfCannotFindById(sfc, "Service Function Chain", serviceFunctionChain.getElementId());

        return new ArrayList<>(sfc.getPortPairGroups());
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
        this.txControl.required(() -> {
            this.em.close();
            return null;
        });
    }

}
