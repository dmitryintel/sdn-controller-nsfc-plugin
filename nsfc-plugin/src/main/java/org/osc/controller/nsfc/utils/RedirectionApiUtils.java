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
import static java.util.Collections.emptyList;
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
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.NetworkElementEntity;
import org.osc.controller.nsfc.entities.PortPairGroupEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.sdk.controller.element.NetworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectionApiUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectionApiUtils.class);

    private OsCalls osCalls;

    public RedirectionApiUtils(OsCalls osCalls) {
        this.osCalls = osCalls;
    }

    private NetworkElementEntity makeNetworkElementEntity(Port port, String parentId) {
        checkArgument(port != null, "null passed for %s !", "OS Port");

        List<String> ips = new ArrayList<>();
        if (port.getFixedIps() != null) {
            ips = port.getFixedIps().stream().map(ip -> ip.getIpAddress()).collect(Collectors.toList());
        }
        return new NetworkElementEntity(port.getId(), singletonList(port.getMacAddress()), ips, parentId);
    }

    private InspectionPortEntity makeInspectionPortEntity(PortPair portPair) {
        checkArgument(portPair != null, "null passed for %s !", "Inspection Port");

        Port ingressPort = portPair.getIngressId() != null ? this.osCalls.getPort(portPair.getIngressId())
                            : null;
        Port egressPort = portPair.getEgressId() != null ? this.osCalls.getPort(portPair.getEgressId())
                            : null;

        NetworkElementEntity ingressEntity = null;
        if (ingressPort != null) {
            ingressEntity = makeNetworkElementEntity(ingressPort, portPair.getId());
        }

        NetworkElementEntity egressEntity = null;
        if (egressPort != null) {
            egressEntity = makeNetworkElementEntity(egressPort, portPair.getId());
        }

        return new InspectionPortEntity(portPair.getId(), null,
                                        ingressEntity, egressEntity);
    }

    private PortPairGroupEntity makePortPairGroupEntity(PortPairGroup portPairGroup) {
        checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");
        PortPairGroupEntity retVal = new PortPairGroupEntity(portPairGroup.getId());

        if (portPairGroup.getPortPairs() != null) {
            List<? extends PortPair> portPairs = this.osCalls.listPortPairs();
            portPairs = portPairs
                         .stream()
                         .filter(pp -> portPairGroup.getPortPairs().contains(pp.getId()))
                         .collect(Collectors.toList());

            for (PortPair portPair : portPairs) {
                try {
                    InspectionPortEntity inspectionPortEntity = makeInspectionPortEntity(portPair);
                    retVal.getPortPairs().add(inspectionPortEntity);
                    inspectionPortEntity.setPortPairGroup(retVal);
                } catch (IllegalArgumentException e) {
                    LOG.error("Port pair {}  listed for port pair group {} does not exist!", portPair.getId(),
                              portPairGroup.getId());
                }
            }

        }

        return retVal;
    }

    private ServiceFunctionChainEntity makeServiceFunctionChainEntity(PortChain portChain) {

        ServiceFunctionChainEntity retVal =  new ServiceFunctionChainEntity(portChain.getId());

        if (portChain.getPortPairGroups() != null) {
            Set<? extends PortPairGroup> portPairGroups = new HashSet<>(this.osCalls.listPortPairGroups());
            portPairGroups = portPairGroups
                                 .stream()
                                 .filter(pp -> portChain.getPortPairGroups().contains(pp.getId()))
                                 .collect(toSet());

            for (PortPairGroup portPairGroup : portPairGroups) {
                try {
                    PortPairGroupEntity portPairGroupEntity = makePortPairGroupEntity(portPairGroup);
                    retVal.getPortPairGroups().add(portPairGroupEntity);
                    portPairGroupEntity.setServiceFunctionChain(retVal);
                } catch (IllegalArgumentException e) {
                    LOG.error("Port pair group {} listed for port chain group {} does not exist!", portPairGroup.getId(),
                              portChain.getId());
                }
            }
        }

        return retVal;
    }

    public InspectionHookEntity makeInspectionHookEntity(String flowClassifierId) {
        FlowClassifier flowClassifier = this.osCalls.getFlowClassifier(flowClassifierId);

        if (flowClassifier == null) {
            return null;
        }

        Port port = findProtectedPort(flowClassifier);

        if (port != null) {
            NetworkElementEntity inspectedPort = makeNetworkElementEntity(port, flowClassifier.getId());
            InspectionHookEntity retVal = new InspectionHookEntity(inspectedPort, null);
            retVal.setHookId(flowClassifierId);
            return retVal;
        }

        return null;
    }

    public Port findProtectedPort(FlowClassifier flowClassifier) {
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

    private InspectionPortEntity findPortPairUnderGroup(PortPairGroupEntity portPairGroupEntity, String portPairId) {
        checkArgument(portPairGroupEntity != null, "null passed for %s !", "Port Pair Group Entity");
        checkArgument(portPairId != null, "null passed for %s !", "Port Pair Id");
        if (portPairGroupEntity.getPortPairs() != null) {
            return portPairGroupEntity.getPortPairs().stream().filter(pp -> portPairId.equals(pp.getElementId()))
                            .findFirst().orElse(null);
        }

        return null;
    }

    private PortPairGroupEntity findIn(ServiceFunctionChainEntity sfcEntity, String portPairGroupId) {
        checkArgument(sfcEntity != null, "null passed for %s !", "Service Function Chain");
        checkArgument(portPairGroupId != null, "null passed for %s !", "Port Pair Group Id");
        if (sfcEntity.getPortPairGroups() != null) {
            return sfcEntity.getPortPairGroups().stream().filter(ppg -> portPairGroupId.equals(ppg.getElementId()))
                            .findFirst().orElse(null);
        }

        return null;
    }

    /**
     * Expensive call: Searches through the list port pairs.
     * @param ingress
     * @param egress
     *
     * @return PortPair
     */
    public PortPair findInspectionPortByNetworkElements(NetworkElement ingress, NetworkElement egress) {
        String ingressId = ingress != null ? ingress.getElementId() : null;
        String egressId = egress != null ? egress.getElementId() : null;

        List<? extends PortPair> portPairs = this.osCalls.listPortPairs();

        Optional<? extends PortPair> portPairOpt = portPairs.stream()
                            .filter(pp -> Objects.equals(ingressId, pp.getIngressId())
                                                && Objects.equals(egressId, pp.getEgressId()))
                            .findFirst();

        return portPairOpt.orElse(null);
    }

    public PortPairGroup findContainingPortPairGroup(String portPairId) {
        List<? extends PortPairGroup> portPairGroups = this.osCalls.listPortPairGroups();
        Optional<? extends PortPairGroup> ppgOpt = portPairGroups.stream()
                                        .filter(ppg -> ppg.getPortPairs().contains(portPairId))
                                        .findFirst();
        return ppgOpt.orElse(null);
    }

    public PortChain findContainingPortChain(String portPairGroupId) {
        List<? extends PortChain> portChains = this.osCalls.listPortChains();
        Optional<? extends PortChain> pcOpt = portChains.stream()
                                        .filter(pc -> pc.getPortPairGroups().contains(portPairGroupId))
                                        .findFirst();
        return pcOpt.orElse(null);
    }

    public PortChain findContainingPortChainForFC(String flowClassifierId) {
        List<? extends PortChain> portChains = this.osCalls.listPortChains();
        Optional<? extends PortChain> pcOpt = portChains.stream()
                                        .filter(pc -> pc.getFlowClassifiers() != null
                                                          && pc.getFlowClassifiers().contains(flowClassifierId))
                                        .findFirst();
        return pcOpt.orElse(null);
    }

    /**
     * Fetches InspectionPortEntity from openStack, with full dependencies,
     * including parent (Port Pair Group) and parent's parent (Service Function Chain.)
     * <p/>
     *
     * Very expensive call: potentially does three list() calls on OS.
     *
     * @param portPair assumed not null
     * @return InspectionPortEntity
     */
    public InspectionPortEntity findComplete(PortPair portPair) {
        PortPairGroup portPairGroup = findContainingPortPairGroup(portPair.getId());
        if (portPairGroup != null) {
            PortChain portChain = findContainingPortChain(portPairGroup.getId());
            PortPairGroupEntity portPairGroupEntity;
            if (portChain != null) {
                ServiceFunctionChainEntity serviceFunctionChainEntity =
                        makeServiceFunctionChainEntity(portChain);
                portPairGroupEntity =
                        findIn(serviceFunctionChainEntity, portPairGroup.getId());
            } else {
                portPairGroupEntity = makePortPairGroupEntity(portPairGroup);
            }
            return findPortPairUnderGroup(portPairGroupEntity, portPair.getId());
        } else {
            return makeInspectionPortEntity(portPair);
        }
    }

    /**
     * Expensive call: Searches through the list port chains.
     * @param portPairGroup assumed not null
     * @return PortPairGroupEntity
     */
    public PortPairGroupEntity findComplete(PortPairGroup portPairGroup) {
       PortChain portChain = findContainingPortChain(portPairGroup.getId());
       PortPairGroupEntity portPairGroupEntity;
       if (portChain != null) {
           ServiceFunctionChainEntity serviceFunctionChainEntity =
                   makeServiceFunctionChainEntity(portChain);
           portPairGroupEntity =
                   findIn(serviceFunctionChainEntity, portPairGroup.getId());
       } else {
           portPairGroupEntity = makePortPairGroupEntity(portPairGroup);
       }

       return portPairGroupEntity;
   }

    public ServiceFunctionChainEntity findComplete(PortChain portChain) {
        return makeServiceFunctionChainEntity(portChain);
    }

    /**
     * Assumes argument is not null
     */
    public FlowClassifier findInspHookByInspectedPort(NetworkElement inspected) {
        LOG.info("Finding Inspection hooks for inspected port {}", inspected);

        Port inspectedPort  = this.osCalls.getPort(inspected.getElementId());

        if (inspectedPort == null) {
            throw new IllegalArgumentException(String.format("Inspected port %s does not exist!", inspected.getElementId()));
        }

        if (inspectedPort.getProfile() == null || inspectedPort.getProfile().get(KEY_HOOK_ID) == null) {
            LOG.warn("No Inspection hooks for inspected port {}", inspected.getElementId());
            return null;
        }

        String hookId = (String) inspectedPort.getProfile().get(KEY_HOOK_ID);
        FlowClassifier flowClassifier = this.osCalls.getFlowClassifier(hookId);

        if (flowClassifier == null) {
            setHookOnPort(inspectedPort.getId(), null);
            LOG.warn("Inspection hook {} for inspected port {} no longer exists!",
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

    public NetworkElementEntity retrieveNetworkElementFromOS(String portId, String portPairId) {
        if (portId == null) {
            return null;
        }

        Port port = this.osCalls.getPort(portId);

        if (port == null) {
            LOG.error("Port {} not found on openstack", portId);
            return null;
        }

        List<String> ips = emptyList();
        if (port.getFixedIps() != null) {
            ips = port.getFixedIps().stream().map(ip -> ip.getIpAddress()).collect(Collectors.toList());
        }

        return new NetworkElementEntity(port.getId(), ips, singletonList(port.getMacAddress()), portPairId);
    }

    public void validatePPGList(List<NetworkElement> portPairGroups) {
        List<? extends PortChain> portChains = this.osCalls.listPortChains();
        for (NetworkElement ne : portPairGroups) {
            checkArgument(ne != null && ne.getElementId() != null,
                         "null passed for %s !", "Port Pair Group Id");

            PortPairGroup portPairGroup = this.osCalls.getPortPairGroup(ne.getElementId());

            checkArgument(portPairGroup != null && portPairGroup.getId() != null,
                          "Cannot find %s by id: %s!", "Port Pair Group", ne.getElementId());

            Optional<? extends PortChain> pcMaybe = portChains.stream()
                                                    .filter(pc -> pc.getPortPairGroups().contains(portPairGroup.getId()))
                                                    .findFirst();
            if (pcMaybe.isPresent()) {
                throw new IllegalArgumentException(
                        String.format("Port Pair Group Id %s is already chained to SFC Id : %s ", ne.getElementId(),
                                pcMaybe.get().getId()));
            }
        }
    }

    public FlowClassifier buildFlowClassifier(String inspectedPortIp, ServiceFunctionChainEntity sfcEntity) {
        FlowClassifier flowClassifier;
        String sourcePortId = sfcEntity.getPortPairGroups().get(0).getPortPairs().get(0).getIngressPort().getElementId();
        int nGroups = sfcEntity.getPortPairGroups().size();
        String destPortId = sfcEntity.getPortPairGroups().get(nGroups - 1).getPortPairs().get(0).getEgressPort().getElementId();

        flowClassifier = Builders.flowClassifier()
                             .destinationIpPrefix(inspectedPortIp)
                             .logicalSourcePort(sourcePortId)
                             .logicalDestinationPort(destPortId)
                             .build();
        return flowClassifier;
    }

}
