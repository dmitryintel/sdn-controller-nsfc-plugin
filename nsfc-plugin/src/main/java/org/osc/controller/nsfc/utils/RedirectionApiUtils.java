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
package org.osc.controller.nsfc.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi.KEY_HOOK_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstack4j.api.Builders;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.openstack4j.model.network.options.PortListOptions;
import org.osc.controller.nsfc.entities.NetworkElementImpl;
import org.osc.controller.nsfc.entities.PortPairElement;
import org.osc.controller.nsfc.entities.PortPairGroupElement;
import org.osc.controller.nsfc.entities.ServiceFunctionChainElement;
import org.osc.sdk.controller.element.NetworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectionApiUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectionApiUtils.class);

    private OsCalls osCalls;

    public RedirectionApiUtils(OsCalls osCalls) {
        this.osCalls = osCalls;
    }

    private NetworkElementImpl fetchNetworkElementWithChildDepends(Port port, String parentId) {
        checkArgument(port != null, "null passed for %s !", "OS Port");

        List<String> ips = new ArrayList<>();
        if (port.getFixedIps() != null) {
            ips = port.getFixedIps().stream().map(ip -> ip.getIpAddress()).collect(Collectors.toList());
        }
        return new NetworkElementImpl(port.getId(), singletonList(port.getMacAddress()), ips, parentId);
    }

    private PortPairElement fetchPortPairWithChildDepends(PortPair portPair) {
        checkArgument(portPair != null, "null passed for %s !", "Port Pair");

        Port ingressPort = portPair.getIngressId() != null ? this.osCalls.getPort(portPair.getIngressId())
                            : null;
        Port egressPort = portPair.getEgressId() != null ? this.osCalls.getPort(portPair.getEgressId())
                            : null;

        NetworkElementImpl ingressElement = null;
        if (ingressPort != null) {
            ingressElement = fetchNetworkElementWithChildDepends(ingressPort, portPair.getId());
        }

        NetworkElementImpl egressElement = null;
        if (egressPort != null) {
            egressElement = fetchNetworkElementWithChildDepends(egressPort, portPair.getId());
        }

        return new PortPairElement(portPair.getId(), null,
                                        ingressElement, egressElement);
    }

    private PortPairGroupElement fetchPortPairGroupWithChildDepends(PortPairGroup portPairGroup) {
        checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");
        PortPairGroupElement retVal = new PortPairGroupElement(portPairGroup.getId());

        if (portPairGroup.getPortPairs() != null) {
            List<? extends PortPair> portPairs = this.osCalls.listPortPairs();
            portPairs = portPairs
                         .stream()
                         .filter(pp -> portPairGroup.getPortPairs().contains(pp.getId()))
                         .collect(Collectors.toList());

            for (PortPair portPair : portPairs) {
                try {
                    PortPairElement portPairElement = fetchPortPairWithChildDepends(portPair);
                    retVal.getPortPairs().add(portPairElement);
                    portPairElement.setPortPairGroup(retVal);
                } catch (IllegalArgumentException e) {
                    LOG.error("Port Pair {} listed for port pair group {} does not exist!", portPair.getId(),
                              portPairGroup.getId());
                }
            }
        }

        return retVal;
    }

    public ServiceFunctionChainElement fetchSFCWithChildDepends(PortChain portChain) {

        ServiceFunctionChainElement retVal =  new ServiceFunctionChainElement(portChain.getId());

        if (portChain.getPortPairGroups() != null) {
            Set<? extends PortPairGroup> portPairGroups = new HashSet<>(this.osCalls.listPortPairGroups());
            portPairGroups = portPairGroups
                                 .stream()
                                 .filter(pp -> portChain.getPortPairGroups().contains(pp.getId()))
                                 .collect(toSet());

            for (PortPairGroup portPairGroup : portPairGroups) {
                try {
                    PortPairGroupElement portPairGroupElement = fetchPortPairGroupWithChildDepends(portPairGroup);
                    retVal.getPortPairGroups().add(portPairGroupElement);
                    portPairGroupElement.setServiceFunctionChain(retVal);
                } catch (IllegalArgumentException e) {
                    LOG.error("Port Pair Group {} listed for port chain group {} does not exist!", portPairGroup.getId(),
                              portChain.getId());
                }
            }
        }

        return retVal;
    }

    public Port fetchProtectedPort(FlowClassifier flowClassifier) {
        String ip = flowClassifier.getDestinationIpPrefix();

        if (ip != null && ip.matches("^.*/32$")) {
            ip = ip.substring(0, ip.length() - 3);
        }

        PortListOptions options = PortListOptions.create().tenantId(flowClassifier.getTenantId());
        options.getOptions().put("ip_address", ip);

        List<? extends Port> ports = this.osCalls.listPorts();
        Port port = ports.stream().filter(p -> p.getProfile() != null
                                            && flowClassifier.getId().equals(p.getProfile().get(KEY_HOOK_ID)))
                          .findFirst().orElse(null);
        return port;
    }

    /**
     * Expensive call: Searches through the list port pairs from openstack.
     * @param ingress
     * @param egress
     *
     * @return PortPair
     */
    public PortPair fetchInspectionPortByNetworkElements(NetworkElement ingress, NetworkElement egress) {
        String ingressId = ingress != null ? ingress.getElementId() : null;
        String egressId = egress != null ? egress.getElementId() : null;

        List<? extends PortPair> portPairs = this.osCalls.listPortPairs();

        return portPairs.stream()
                        .filter(pp -> Objects.equals(ingressId, pp.getIngressId())
                                            && Objects.equals(egressId, pp.getEgressId()))
                        .findFirst()
                        .orElse(null);
    }

    public PortPairGroup fetchContainingPortPairGroup(String portPairId) {
        List<? extends PortPairGroup> portPairGroups = this.osCalls.listPortPairGroups();
        Optional<? extends PortPairGroup> ppgOpt = portPairGroups.stream()
                                        .filter(ppg -> ppg.getPortPairs().contains(portPairId))
                                        .findFirst();
        return ppgOpt.orElse(null);
    }

    public PortChain fetchContainingPortChain(String portPairGroupId) {
        List<? extends PortChain> portChains = this.osCalls.listPortChains();
        Optional<? extends PortChain> pcOpt = portChains.stream()
                                        .filter(pc -> pc.getPortPairGroups().contains(portPairGroupId))
                                        .findFirst();
        return pcOpt.orElse(null);
    }

    public PortChain fetchContainingPortChainForFC(String flowClassifierId) {
        List<? extends PortChain> portChains = this.osCalls.listPortChains();
        Optional<? extends PortChain> pcOpt = portChains.stream()
                                        .filter(pc -> pc.getFlowClassifiers() != null
                                                          && pc.getFlowClassifiers().contains(flowClassifierId))
                                        .findFirst();
        return pcOpt.orElse(null);
    }

    /**
     * Assumes argument is not null
     */
    public FlowClassifier fetchInspHookByInspectedPort(NetworkElement inspected) {
        LOG.info("Finding Flow Classifiers for inspected port {}", inspected);

        Port inspectedPort  = this.osCalls.getPort(inspected.getElementId());

        if (inspectedPort == null) {
            throw new IllegalArgumentException(String.format("Flow Classifier %s does not exist!", inspected.getElementId()));
        }

        if (inspectedPort.getProfile() == null || inspectedPort.getProfile().get(KEY_HOOK_ID) == null) {
            LOG.warn("No Flow Classifier for inspected port {}", inspected.getElementId());
            return null;
        }

        String hookId = (String) inspectedPort.getProfile().get(KEY_HOOK_ID);
        FlowClassifier flowClassifier = this.osCalls.getFlowClassifier(hookId);

        if (flowClassifier == null) {
            setHookOnPort(inspectedPort.getId(), null);
            LOG.warn("Flow Classifier {} for inspected port {} no longer exists!",
                     hookId, inspected.getElementId());
            return null;
        }

        return flowClassifier;
    }

    /**
     *
     * @param port
     * @param hookId set to null to un-hook
     * @return modified port
     */
    public void setHookOnPort(String portId, String hookId) {
        Port port = this.osCalls.getPort(portId);

        if (port == null) {
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        if (hookId == null) {
            profile.remove(KEY_HOOK_ID);
        } else {
            profile.put(KEY_HOOK_ID, hookId);
        }

        port = port.toBuilder().profile(profile).build();
        this.osCalls.updatePort(port);
    }

    public FlowClassifier buildFlowClassifier(String inspectedPortIp, ServiceFunctionChainElement sfcElement) {
        FlowClassifier flowClassifier;
        String sourcePortId = sfcElement.getPortPairGroups().get(0).getPortPairs().get(0).getIngressPort().getElementId();
        int nGroups = sfcElement.getPortPairGroups().size();
        String destPortId = sfcElement.getPortPairGroups().get(nGroups - 1).getPortPairs().get(0).getEgressPort().getElementId();

        flowClassifier = Builders.flowClassifier()
                             .description("Flow Classifier created by OSC")
                             .destinationIpPrefix(inspectedPortIp)
                             .logicalSourcePort(sourcePortId)
                             .logicalDestinationPort(destPortId)
                             .build();
        return flowClassifier;
    }
}
