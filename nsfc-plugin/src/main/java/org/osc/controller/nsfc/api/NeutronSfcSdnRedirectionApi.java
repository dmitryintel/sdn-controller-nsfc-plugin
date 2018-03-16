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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.osc.controller.nsfc.utils.ArgumentCheckUtil.throwExceptionIfNullOrEmptyNetworkElementList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.entities.FlowClassifierElement;
import org.osc.controller.nsfc.entities.NetworkElementImpl;
import org.osc.controller.nsfc.entities.PortPairElement;
import org.osc.controller.nsfc.entities.PortPairGroupElement;
import org.osc.controller.nsfc.entities.ServiceFunctionChainElement;
import org.osc.controller.nsfc.utils.OsCalls;
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
    private OsCalls osCalls;

    public NeutronSfcSdnRedirectionApi() {
    }

    public NeutronSfcSdnRedirectionApi(OSClientV3 osClient) {
        this.osCalls = new OsCalls(osClient);
        this.utils = new RedirectionApiUtils(this.osCalls);
    }

    // Inspection port methods
    @Override
    public InspectionPortElement getInspectionPort(InspectionPortElement inspectionPort) throws Exception {
        if (inspectionPort == null) {
            LOG.warn("Attempt to find null InspectionPort");
            return null;
        }

        PortPair portPair = this.utils.fetchPortPairForInspectionPort(inspectionPort);

        if (portPair != null) {
            NetworkElement ingress = inspectionPort.getIngressPort();
            NetworkElement egress = inspectionPort.getEgressPort();
            NetworkElementImpl ingressElement = null;
            NetworkElementImpl egressElement = null;

            if (ingress != null) {
                ingressElement = new NetworkElementImpl(ingress.getElementId(), ingress.getMacAddresses(),
                        ingress.getPortIPs(), ingress.getParentId());
            }

            if (egress != null) {
                egressElement = new NetworkElementImpl(egress.getElementId(), egress.getMacAddresses(),
                        egress.getPortIPs(), egress.getParentId());
            }

            // only id is ever used
            return new PortPairElement(portPair.getId(), null, ingressElement, egressElement);
        }

        return null;
    }

    @Override
    public Element registerInspectionPort(InspectionPortElement inspectionPort) throws Exception {
        if (inspectionPort == null) {
            throw new IllegalArgumentException("Attempt to register null InspectionPort");
        }
        PortPairGroup portPairGroup = null;
        String inspectionPortPairGroupId = inspectionPort.getParentId();

        if (inspectionPortPairGroupId != null) {
            portPairGroup = this.osCalls.getPortPairGroup(inspectionPortPairGroupId);
            checkArgument(portPairGroup != null,
                    "Cannot find %s by id: %s!", "Port Pair Group", inspectionPortPairGroupId);
        }

        NetworkElement ingress = inspectionPort.getIngressPort();
        NetworkElement egress = inspectionPort.getEgressPort();
        PortPair portPair = this.utils.fetchPortPairByNetworkElements(ingress, egress);

        if (portPair == null) {
            portPair = Builders.portPair().egressId(egress.getElementId())
                            .ingressId(ingress.getElementId())
                            .name("OSCPortPair-" + UUID.randomUUID().toString().substring(0, 8))
                            .description("Port Pair created by OSC")
                            .build();
            portPair = this.osCalls.createPortPair(portPair);
            checkArgument(portPair != null, "Failed to create port pair for ingress %s, egress %s!",
                          ingress.getElementId(), egress.getElementId());
        }

        if (portPairGroup == null) {
            portPairGroup = Builders.portPairGroup()
                    .description("Port Pair Group created by OSC")
                    .portPairs(new ArrayList<>())
                    .build();
            portPairGroup.getPortPairs().add(portPair.getId());
            portPairGroup = this.osCalls.createPortPairGroup(portPairGroup);
            inspectionPortPairGroupId = portPairGroup.getId();
        } else {

            if (!portPairGroup.getPortPairs().contains(portPair.getId())) {
                portPairGroup.getPortPairs().add(portPair.getId());
            }

            this.osCalls.updatePortPairGroup(portPairGroup.getId(), portPairGroup);
        }

        NetworkElementImpl ingressElement = null;
        NetworkElementImpl egressElement = null;

        if (ingress != null) {
            ingressElement = new NetworkElementImpl(ingress.getElementId(), ingress.getMacAddresses(),
                                                     ingress.getPortIPs(), ingress.getParentId());
        }

        if (egress != null) {
            egressElement = new NetworkElementImpl(egress.getElementId(), egress.getMacAddresses(),
                    egress.getPortIPs(), egress.getParentId());
        }

        // Only parent id of the return value is ever used
        PortPairGroupElement ppgElement = new PortPairGroupElement(inspectionPortPairGroupId);
        PortPairElement retVal = new PortPairElement(portPair.getId(), ppgElement, ingressElement, egressElement);
        ppgElement.getPortPairs().add(retVal);
        return retVal;
    }

    @Override
    public void removeInspectionPort(InspectionPortElement inspectionPort)
            throws NetworkPortNotFoundException, Exception {
        if (inspectionPort == null) {
            LOG.warn("Attempt to remove a null Inspection Port");
            return;
        }

        PortPair portPair = this.utils.fetchPortPairForInspectionPort(inspectionPort);

        if (portPair != null) {
            PortPairGroup portPairGroup = this.utils.fetchContainingPortPairGroup(portPair.getId());

            if (portPairGroup != null) {
                portPairGroup.getPortPairs().remove(portPair.getId());

                if (portPairGroup.getPortPairs().size() > 0) {
                    PortPairGroup ppgUpdate = Builders.portPairGroup().portPairs(portPairGroup.getPortPairs()).build();
                    this.osCalls.updatePortPairGroup(portPairGroup.getId(), ppgUpdate);
                } else {
                    PortChain portChain = this.utils.fetchContainingPortChain(portPairGroup.getId());

                    if (portChain != null) {
                        List<String> ppgIds = portChain.getPortPairGroups();
                        ppgIds.remove(portPairGroup.getId());

                        // service function chain with with no port pair should be allowed to exist?
                        PortChain portChainUpdate = Builders.portChain().portPairGroups(ppgIds).build();
                        this.osCalls.updatePortChain(portChain.getId(), portChainUpdate);
                    }
                    this.osCalls.deletePortPairGroup(portPairGroup.getId());
                }
            }

            this.osCalls.deletePortPair(portPair.getId());
        } else {
            LOG.warn("Attempt to remove nonexistent Port Pair for ingress {} and egress {}",
                    inspectionPort.getIngressPort(), inspectionPort.getEgressPort());
        }
    }

    // Inspection Hooks methods
    @Override
    public String installInspectionHook(NetworkElement inspectedPortElement,
                                        InspectionPortElement inspectionPortElement, Long tag,
                                        TagEncapsulationType encType, Long order,
                                        FailurePolicyType failurePolicyType)
            throws NetworkPortNotFoundException, Exception {

        checkArgument(inspectedPortElement != null && inspectedPortElement.getElementId() != null,
                      "null passed for %s !", "Inspected Port");
        checkArgument(inspectionPortElement != null && inspectionPortElement.getElementId() != null,
                      "null passed for %s !", "Service Function Chain");

        LOG.info("Installing Inspection Hook for (Inspected Port {} ; Inspection Port {}):",
                inspectedPortElement, inspectionPortElement);

        PortChain portChain = this.osCalls.getPortChain(inspectionPortElement.getElementId());
        checkArgument(portChain != null,
                      "Cannot find %s by id: %s!", "Service Function Chain", inspectionPortElement.getElementId());

        FlowClassifier flowClassifier = this.utils.buildFlowClassifier(inspectedPortElement.getElementId());

        flowClassifier = this.osCalls.createFlowClassifier(flowClassifier);
        portChain.getFlowClassifiers().add(flowClassifier.getId());
        this.osCalls.updatePortChain(portChain.getId(), portChain);

        return flowClassifier.getId();
    }

    @Override
    public void updateInspectionHook(InspectionHookElement providedHook) throws Exception {

        if (providedHook == null || providedHook.getHookId() == null) {
            throw new IllegalArgumentException("Attempt to update a null Inspection Hook!");
        }

        LOG.info("Updating Inspection Hook {}:", providedHook);

        NetworkElement providedInspectedPort = providedHook.getInspectedPort();
        InspectionPortElement providedInspectionPort = providedHook.getInspectionPort();
        checkArgument(providedInspectedPort != null && providedInspectedPort.getElementId() != null,
                      "null passed for %s !", "Inspected port");
        checkArgument(providedInspectionPort != null && providedInspectionPort.getElementId() != null,
                      "null passed for %s !", "Service Function Chain");

        FlowClassifier flowClassifier = this.osCalls.getFlowClassifier(providedHook.getHookId());
        checkArgument(flowClassifier != null, "Cannot find Flow Classifier %s", providedHook.getHookId());;

        Port protectedPort = this.utils.fetchProtectedPort(flowClassifier);

        // Detect attempt to re-write the inspected hook
        Set<String> ipsProtected = protectedPort.getFixedIps().stream().map(ip -> ip.getIpAddress()).collect(Collectors.toSet());
        // We don't really handle multiple ip addresses yet.
        if (!ipsProtected.containsAll(providedInspectedPort.getPortIPs())) {
            throw new IllegalStateException(
                    String.format("Cannot update Inspected Port from %s to %s for the Flow Classifier %s",
                            providedInspectedPort.getElementId(), protectedPort.getId(), flowClassifier.getId()));
        }

        PortChain providedPortChain = this.osCalls.getPortChain(providedInspectionPort.getElementId());
        checkArgument(providedPortChain != null, "null passed for %s !", "Service Function Chain");

        PortChain currentPortChain = this.utils.fetchContainingPortChainForFC(flowClassifier.getId());

        if (currentPortChain != null) {
            if (currentPortChain.getId().equals(providedInspectionPort.getElementId())) {
                return;
            }
            currentPortChain.getFlowClassifiers().remove(flowClassifier.getId());
        }

        if (!providedPortChain.getFlowClassifiers().contains(flowClassifier.getId())) {
            providedPortChain.getFlowClassifiers().add(flowClassifier.getId());
        }

        this.osCalls.updatePortChain(currentPortChain.getId(), currentPortChain);
        this.osCalls.updatePortChain(providedPortChain.getId(), providedPortChain);
    }

    @Override
    public void removeInspectionHook(String inspectionHookId) throws Exception {
        if (inspectionHookId == null) {
            LOG.warn("Attempt to remove an Inspection Hook with null id");
            return;
        }

        FlowClassifier flowClassifier = this.osCalls.getFlowClassifier(inspectionHookId);
        if (flowClassifier == null) {
            LOG.warn("Flow Classifier {} does not exist on openstack", inspectionHookId);
            return;
        }

        PortChain portChain = this.utils.fetchContainingPortChainForFC(flowClassifier.getId());
        if (portChain != null) {
            portChain.getFlowClassifiers().remove(flowClassifier.getId());
            this.osCalls.updatePortChain(portChain.getId(), portChain);
        }

        ActionResponse result = this.osCalls.deleteFlowClassifier(flowClassifier.getId());
        if (result.getFault() != null) {
            LOG.error("Error removing flow classifier {}. Response {} ({})", flowClassifier.getId(),
                      result.getCode(), result.getFault());
        }
    }

    @Override
    public InspectionHookElement getInspectionHook(String inspectionHookId) throws Exception {
        if (inspectionHookId == null) {
            LOG.warn("Attempt to get Inspection Hook with null id");
            return null;
        }

        FlowClassifier flowClassifier = this.osCalls.getFlowClassifier(inspectionHookId);

        if (flowClassifier == null) {
            LOG.warn("No flow classifier for id %s", inspectionHookId);
            return null;
        }

        FlowClassifierElement retVal = new FlowClassifierElement(inspectionHookId);
        PortChain portChain = this.utils.fetchContainingPortChainForFC(inspectionHookId);

        // only inspectionPort part of the returned object is ever used, which is SFC
        if (portChain != null) {
            ServiceFunctionChainElement sfcElement = new ServiceFunctionChainElement(portChain.getId());
            retVal.setServiceFunctionChain(sfcElement);
            sfcElement.getInspectionHooks().add(retVal);
        }

        return retVal;
    }

    // SFC methods
    @Override
    public NetworkElement registerNetworkElement(List<NetworkElement> portPairGroupList) throws Exception {
        //check for null or empty list
        throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group member list");

        List<String> portPairGroupIds = portPairGroupList
                                            .stream()
                                            .map(ppg -> ppg.getElementId())
                                            .collect(toList());

        PortChain portChain = Builders.portChain()
                                    .description("Port Chain object created by OSC")
                                    .name("OSCPortChain-" + UUID.randomUUID().toString().substring(0, 8))
                                    .chainParameters(emptyMap())
                                    .flowClassifiers(emptyList())
                                    .portPairGroups(portPairGroupIds)
                                    .build();

        PortChain portChainCreated = this.osCalls.createPortChain(portChain);

        List<PortPairGroupElement> portPairGroups =
                portPairGroupList.stream().map(p -> new PortPairGroupElement(p.getElementId())).collect(toList());

        ServiceFunctionChainElement retVal = new ServiceFunctionChainElement(portChainCreated.getId());
        portPairGroups.stream().forEach(p -> p.setServiceFunctionChain(retVal));
        retVal.setPortPairGroups(portPairGroups);

        return retVal;
    }

    @Override
    public NetworkElement updateNetworkElement(NetworkElement serviceFunctionChain, List<NetworkElement> portPairGroupList)
            throws Exception {
        checkArgument(serviceFunctionChain != null && serviceFunctionChain.getElementId() != null,
                "null passed for %s !", "Service Function Chain Id");
        throwExceptionIfNullOrEmptyNetworkElementList(portPairGroupList, "Port Pair Group update member list");

        PortChain portChain = this.osCalls.getPortChain(serviceFunctionChain.getElementId());
        checkArgument(portChain != null,
                      "Cannot find %s by id: %s!", "Service Function Chain", serviceFunctionChain.getElementId());

        portChain = Builders.portChain().from(portChain)
                            .portPairGroups(Collections.emptyList()).build();
        this.osCalls.updatePortChain(portChain.getId(), portChain);

        List<String> portPairGroupIds = portPairGroupList
                .stream()
                .map(ppg -> ppg.getElementId())
                .collect(toList());

        portChain = Builders.portChain().portPairGroups(portPairGroupIds).build();
        PortChain portChainUpdated = this.osCalls.updatePortChain(serviceFunctionChain.getElementId(), portChain);

        List<PortPairGroupElement> portPairGroups =
                portPairGroupIds.stream().map(id -> new PortPairGroupElement(id)).collect(toList());
        ServiceFunctionChainElement retVal = new ServiceFunctionChainElement(portChainUpdated.getId());
        portPairGroups.stream().forEach(p -> p.setServiceFunctionChain(retVal));
        retVal.setPortPairGroups(portPairGroups);
        return retVal;
    }

    @Override
    public void deleteNetworkElement(NetworkElement serviceFunctionChain) throws Exception {
        checkArgument(serviceFunctionChain != null && serviceFunctionChain.getElementId() != null,
                      "null passed for %s !", "Service Function Chain Id");

        ActionResponse response = this.osCalls.deletePortChain(serviceFunctionChain.getElementId());

        if (!response.isSuccess()) {
            throw new Exception("Exception deleting SFC " + serviceFunctionChain.getElementId()
                        + ". Status " + response.getCode() + "\nMessage:\n" + response.getFault());
        }
    }

    @Override
    public List<NetworkElement> getNetworkElements(NetworkElement serviceFunctionChain) throws Exception {
        checkArgument(serviceFunctionChain != null && serviceFunctionChain.getElementId() != null,
                      "null passed for %s !", "Service Function Chain Id");

        PortChain portChain = this.osCalls.getPortChain(serviceFunctionChain.getElementId());

        checkArgument(portChain != null,
                      "Cannot find %s by id: %s!", "Service Function Chain", serviceFunctionChain.getElementId());

        ArrayList<PortPairGroupElement> portPairGroupElements = new ArrayList<>();

        for (String portPairGroupId : portChain.getPortPairGroups()) {

            // Only ids of the PPG entities are used
            PortPairGroupElement portPairGroupElement = new PortPairGroupElement(portPairGroupId);

            portPairGroupElements.add(portPairGroupElement);
        }

        return new ArrayList<>(portPairGroupElements);
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
    }
}
