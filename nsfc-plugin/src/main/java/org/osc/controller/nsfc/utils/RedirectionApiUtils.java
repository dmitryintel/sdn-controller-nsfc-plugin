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

import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.ActionResponse;
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
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.NetworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectionApiUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectionApiUtils.class);

    private OSClientV3 osClient;

    public RedirectionApiUtils(OSClientV3 osClient) {
        this.osClient = osClient;
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

        Port ingressPort = portPair.getIngressId() != null ? this.osClient.networking().port().get(portPair.getIngressId())
                            : null;
        Port egressPort = portPair.getEgressId() != null ? this.osClient.networking().port().get(portPair.getEgressId())
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
            List<? extends PortPair> portPairs = this.osClient.sfc().portpairs().list();
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
                    LOG.error("Port pair %s  listed for port pair group %s does not exist!", portPair.getId(),
                              portPairGroup.getId());
                }
            }

        }

        return retVal;
    }

    private ServiceFunctionChainEntity makeServiceFunctionChainEntity(PortChain portChain) {

        ServiceFunctionChainEntity retVal =  new ServiceFunctionChainEntity(portChain.getId());

        if (portChain.getPortPairGroups() != null) {
            Set<? extends PortPairGroup> portPairGroups = new HashSet<>(this.osClient.sfc().portpairgroups().list());
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
                    LOG.error("Port pair group %s  listed for port chain group %s does not exist!", portPairGroup.getId(),
                              portChain.getId());
                }
            }
        }

        return retVal;
    }

    public InspectionHookEntity makeInspectionHookEntity(String flowClassifierId) {
        FlowClassifier flowClassifier = getFlowClassifier(flowClassifierId);

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

        List<? extends Port> ports = this.osClient.networking().port().list(options);
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

    public PortPair findInspectionPortByNetworkElements(NetworkElement ingress, NetworkElement egress) {
        String ingressId = ingress != null ? ingress.getElementId() : null;
        String egressId = egress != null ? egress.getElementId() : null;

        List<? extends PortPair> portPairs = this.osClient.sfc().portpairs().list();

        Optional<? extends PortPair> portPairOpt = portPairs.stream()
                            .filter(pp -> Objects.equals(ingressId, pp.getIngressId())
                                                && Objects.equals(egressId, pp.getEgressId()))
                            .findFirst();

        return portPairOpt.orElse(null);
    }

    public PortPairGroup findContainingPortPairGroup(String portPairId) {
        List<? extends PortPairGroup> portPairGroups = this.osClient.sfc().portpairgroups().list();
        Optional<? extends PortPairGroup> ppgOpt = portPairGroups.stream()
                                        .filter(ppg -> ppg.getPortPairs().contains(portPairId))
                                        .findFirst();
        return ppgOpt.orElse(null);
    }

    public PortChain findContainingPortChain(String portPairGroupId) {
        List<? extends PortChain> portChains = this.osClient.sfc().portchains().list();
        Optional<? extends PortChain> pcOpt = portChains.stream()
                                        .filter(pc -> pc.getPortPairGroups().contains(portPairGroupId))
                                        .findFirst();
        return pcOpt.orElse(null);
    }

    public PortChain findContainingPortChainForFC(String flowClassifierId) {
        List<? extends PortChain> portChains = this.osClient.sfc().portchains().list();
        Optional<? extends PortChain> pcOpt = portChains.stream()
                                        .filter(pc -> pc.getFlowClassifiers() != null
                                                          && pc.getFlowClassifiers().contains(flowClassifierId))
                                        .findFirst();
        return pcOpt.orElse(null);
    }

    /**
     * Very expensive call: does full object search
     * @param portPair assumed not null
     * @return
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
     * Expensive call: does full object search
     * @param portPairGroup assumed not null
     * @return
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

    public void removeSingleInspectionHook(String hookId) {
        if (hookId == null) {
            LOG.warn("Attempt to remove Inspection Hook with null id");
            return;
        }

        deleteFlowClassifier(hookId);
    }

    /**
     * Assumes arguments is not null
     */
    public FlowClassifier findInspHookByInspectedPort(NetworkElement inspected) {
        LOG.info(String.format("Finding Inspection hooks for inspected port %s", inspected));

        Port inspectedPort  = this.osClient.networking().port().get(inspected.getElementId());

        if (inspectedPort == null) {
            throw new IllegalArgumentException(String.format("Inspected port %s does not exist!", inspected.getElementId()));
        }

        if (inspectedPort.getProfile() == null || inspectedPort.getProfile().get(KEY_HOOK_ID) == null) {
            LOG.warn(String.format("No Inspection hooks for inspected port %s", inspected.getElementId()));
            return null;
        }

        String hookId = (String) inspectedPort.getProfile().get(KEY_HOOK_ID);
        FlowClassifier flowClassifier = this.osClient.sfc().flowclassifiers().get(hookId);

        if (flowClassifier == null) {
            setHookOnPort(inspectedPort.getId(), null);
            LOG.warn(String.format("Inspection hook %s for inspected port %s no longer exists!",
                     hookId, inspected.getElementId()));
            return null;
        }

        return flowClassifier;
    }

    public NetworkElementEntity retrieveNetworkElementFromOS(String portId, String portPairId) {
        if (portId == null) {
            return null;
        }

        Port port = this.osClient.networking().port().get(portId);

        if (port == null) {
            LOG.error("Port {} not found on openstack {}", portId, this.osClient.getEndpoint());
            return null;
        }

        List<String> ips = emptyList();
        if (port.getFixedIps() != null) {
            ips = port.getFixedIps().stream().map(ip -> ip.getIpAddress()).collect(Collectors.toList());
        }

        return new NetworkElementEntity(port.getId(), ips, singletonList(port.getMacAddress()), portPairId);
    }

    public void validatePPGList(List<NetworkElement> portPairGroups) {
        List<? extends PortChain> portChains = this.osClient.sfc().portchains().list();
        for (NetworkElement ne : portPairGroups) {
            checkArgument(ne != null && ne.getElementId() != null,
                         "null passed for %s !", "Port Pair Group Id");

            PortPairGroup portPairGroup = getPortPairGroup(ne.getElementId());

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

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public void throwExceptionIfNullOrEmptyNetworkElementList(List<NetworkElement> neList, String type) {
        if (neList == null || neList.isEmpty()) {
            String msg = String.format("null passed for %s !", type);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public void throwExceptionIfNullElementAndParentId(Element element, String type) {
       if (element == null || element.getParentId() == null) {
           String msg = String.format("null passed for %s !", type);
           LOG.error(msg);
           throw new IllegalArgumentException(msg);
       }
   }

  /**
   *
   * @param port
   * @param hookId set to null to un-hook
   * @return modified port
   */
  public void setHookOnPort(String portId, String hookId) {
      Port port = this.osClient.networking().port().get(portId);

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
      this.osClient.networking().port().update(port);
  }

  public FlowClassifier createFlowClassifier(FlowClassifier flowClassifier) {
      checkArgument(flowClassifier != null, "null passed for %s !", "Flow Classifier");
      String ip = flowClassifier.getDestinationIpPrefix();

      if (ip != null && !ip.endsWith("/32")) {
          ip += "/32";
      }

      flowClassifier = flowClassifier.toBuilder().id(null).destinationIpPrefix(ip).build();
      return this.osClient.sfc().flowclassifiers().create(flowClassifier);
  }

  public PortChain createPortChain(PortChain portChain) {
      checkArgument(portChain != null, "null passed for %s !", "Port Chain");
      portChain = portChain.toBuilder().id(null).build();
      portChain = this.osClient.sfc().portchains().create(portChain);

      return fixPortChainCollections(portChain);
  }

  public PortPairGroup createPortPairGroup(PortPairGroup portPairGroup) {
      checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");
      portPairGroup = portPairGroup.toBuilder().id(null).build();

      portPairGroup = this.osClient.sfc().portpairgroups().create(portPairGroup);
      return portPairGroup;
  }

  public PortPair createPortPair(PortPair portPair) {
      checkArgument(portPair != null, "null passed for %s !", "Port Pair");
      portPair = portPair.toBuilder().id(null).build();

      portPair = this.osClient.sfc().portpairs().create(portPair);
      return portPair;
  }

  public FlowClassifier getFlowClassifier(String flowClassifierId) {
      return this.osClient.sfc().flowclassifiers().get(flowClassifierId);
  }

  public PortChain getPortChain(String portChainId) {
      PortChain portChain = this.osClient.sfc().portchains().get(portChainId);
      return fixPortChainCollections(portChain);
  }

  public PortPairGroup getPortPairGroup(String portPairGroupId) {
      return this.osClient.sfc().portpairgroups().get(portPairGroupId);
  }

  public PortPair getPortPair(String portPairId) {
      return this.osClient.sfc().portpairs().get(portPairId);
  }

  public Port getPort(String portId) {
      return this.osClient.networking().port().get(portId);
  }

  public FlowClassifier updateFlowClassifier(String flowClassifierId, FlowClassifier flowClassifier) {
      checkArgument(flowClassifierId != null, "null passed for %s !", "Flow Classifier Id");
      checkArgument(flowClassifier != null, "null passed for %s !", "Flow Classifier");

      // OS won't let us modify some attributes. Must be null on update object
      flowClassifier = flowClassifier.toBuilder().id(null).projectId(null).build();

      return this.osClient.sfc().flowclassifiers().update(flowClassifierId, flowClassifier);
  }

  public PortChain updatePortChain(String portChainId, PortChain portChain) {
      checkArgument(portChainId != null, "null passed for %s !", "Port Chain Id");
      checkArgument(portChain != null, "null passed for %s !", "Port Chain");

      // OS won't let us modify some attributes. Must be null on update object
      portChain = portChain.toBuilder().id(null).projectId(null).chainParameters(null).chainId(null).build();

      portChain = this.osClient.sfc().portchains().update(portChainId, portChain);
      return fixPortChainCollections(portChain);
  }

  public PortPairGroup updatePortPairGroup(String portPairGroupId, PortPairGroup portPairGroup) {
      checkArgument(portPairGroupId != null, "null passed for %s !", "Port Pair Group Id");
      checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");

      // OS won't let us modify some attributes. Must be null on update object
      portPairGroup  = portPairGroup.toBuilder().id(null).projectId(null).portPairGroupParameters(null).build();

      portPairGroup = this.osClient.sfc().portpairgroups().update(portPairGroupId, portPairGroup);
      return portPairGroup;
  }

  public PortPair updatePortPair(String portPairId, PortPair portPair) {
      checkArgument(portPairId != null, "null passed for %s !", "Port Pair Id");
      checkArgument(portPair != null, "null passed for %s !", "Port Pair");

      // OS won't let us modify some attributes. Must be null on update object
      portPair = portPair.toBuilder().id(null).projectId(null).build();
      portPair = this.osClient.sfc().portpairs().update(portPairId, portPair);
      return portPair;
  }

  public Port updatePort(Port port) {
      return this.osClient.networking().port().update(port);
  }

  public ActionResponse deleteFlowClassifier(String flowClassifierId) {
      return this.osClient.sfc().flowclassifiers().delete(flowClassifierId);
  }

  public ActionResponse deletePortChain(String portChainId) {
      ActionResponse response = this.osClient.sfc().portchains().delete(portChainId);
      return response;
  }

  public ActionResponse deletePortPairGroup(String portPairGroupId) {
      ActionResponse response = this.osClient.sfc().portpairgroups().delete(portPairGroupId);
      return response;
  }

  public ActionResponse deletePortPair(String portPairId) {
      ActionResponse response = this.osClient.sfc().portpairs().delete(portPairId);
      return response;
  }

  private PortChain fixPortChainCollections(PortChain portChain) {
      if (portChain == null) {
          return null;
      }

      if (portChain.getFlowClassifiers() == null) {
          portChain = portChain.toBuilder().flowClassifiers(new ArrayList<>()).build();
      }

      if (portChain.getPortPairGroups() == null) {
          portChain = portChain.toBuilder().portPairGroups(new ArrayList<>()).build();
      }

      return portChain;
  }
}
