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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.NetworkElementEntity;
import org.osc.controller.nsfc.entities.PortPairGroupEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.InspectionPortElement;
import org.osc.sdk.controller.element.NetworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectionApiUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectionApiUtils.class);

    private OSClientV3 osClient;

    public RedirectionApiUtils(OSClientV3 osClient) {
        this.osClient = osClient;
    }

    private PortPairGroupEntity makePortPairGroupEntity(PortPairGroup portPairGroup) {
        return null; // TODO (Dmitry) Implement
    }

    public NetworkElementEntity makeNetworkElementEntity(NetworkElement networkElement) {
        NetworkElementEntity retVal = new NetworkElementEntity();

        retVal.setElementId(networkElement.getElementId());
        retVal.setMacAddresses(networkElement.getMacAddresses());
        retVal.setPortIPs(networkElement.getPortIPs());

        return retVal;
    }

    public NetworkElementEntity makeNetworkElementEntity(Port port, String parentId) {
        throwExceptionIfNullElement(port, "OS Port");
        List<String> ips = new ArrayList<>();
        if (port.getFixedIps() != null) {
            ips = port.getFixedIps().stream().map(ip -> ip.toString()).collect(Collectors.toList());
        }

        return new NetworkElementEntity(port.getId(), singletonList(port.getMacAddress()), ips, parentId);
    }

    public InspectionPortEntity makeInspectionPortEntity(InspectionPortElement inspectionPortElement) {
        throwExceptionIfNullElement(inspectionPortElement, "Inspection Port");

        NetworkElement ingress = inspectionPortElement.getIngressPort();
        throwExceptionIfNullElement(ingress, "ingress element.");
        NetworkElementEntity ingressEntity = makeNetworkElementEntity(ingress);

        NetworkElement egress = inspectionPortElement.getEgressPort();
        NetworkElementEntity egressEntity = null;
        throwExceptionIfNullElement(egress, "egress element.");

        if (ingressEntity.getElementId().equals(egress.getElementId())) {
            egressEntity = ingressEntity;
        } else {
            egressEntity = makeNetworkElementEntity(egress);
        }
        String ppgId = inspectionPortElement.getParentId();

        PortPairGroup ppg = ppgId != null ? findByPortPairgroupId(ppgId) : null;
        PortPairGroupEntity ppgEntity = ppgId != null ? makePortPairGroupEntity(ppg) : null;

        return new InspectionPortEntity(inspectionPortElement.getElementId(), ppgEntity, ingressEntity, egressEntity);
    }

    public InspectionHookEntity makeInspectionHookEntity(NetworkElement inspectedPort,
            NetworkElement sfcNetworkElement) {

        throwExceptionIfNullElement(inspectedPort, "inspected port!");

        ServiceFunctionChainEntity sfc = findBySfcId(sfcNetworkElement.getElementId());

        NetworkElementEntity inspected = makeNetworkElementEntity(inspectedPort);
        InspectionHookEntity retVal = new InspectionHookEntity(inspected, sfc);

        inspected.setInspectionHook(retVal);

        return retVal;
    }

    public PortPairGroup findByPortPairgroupId(String ppgId) {
        return this.osClient.sfc().portpairgroups().get(ppgId);
    }

    public void removePortPairGroup(String ppgId) {
        // TODO (Dmitry) You have the code in RedirectApi. Factor out to here.
    }

    public ServiceFunctionChainEntity findBySfcId(String sfcId) {
        return null; // TODO (Dmitry) Implement
    }

    public void removeSingleInspectionHook(String hookId) {
        if (hookId == null) {
            LOG.warn("Attempt to remove Inspection Hook with null id");
            return;
        }
     // TODO (Dmitry) Implement
    }

    public void removeSingleInspectionPort(String inspectionPortId) {
     // TODO (Dmitry) Implement
    }

    /**
     * Assumes arguments are not null
     */
    public InspectionHookEntity findInspHookByInspectedAndPort(NetworkElement inspected,
            ServiceFunctionChainEntity inspectionSfc) {
        LOG.info(String.format("Finding Inspection hooks by inspected %s and sfc %s", inspected,
                inspectionSfc.getElementId()));
        return null; // TODO (Dmitry) Implement
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
            throwExceptionIfNullElementAndId(ne, "Port Pair Group Id");
            PortPairGroup ppg = findByPortPairgroupId(ne.getElementId());
            throwExceptionIfCannotFindById(ppg, "Port Pair Group", ne.getElementId());

            Optional<? extends PortChain> pcMaybe = portChains.stream().filter(pc -> pc.getPortPairGroups().contains(ppg.getId()))
                                                    .findFirst();
            if (pcMaybe.isPresent()) {
                throw new IllegalArgumentException(
                        String.format("Port Pair Group Id %s is already chained to SFC Id : %s ", ne.getElementId(),
                                pcMaybe.get().getId()));
            }
        }
    }

    public void validateAndClear(ServiceFunctionChainEntity sfc) {
     // TODO (Dmitry) Implement
    }

    /**
     * TODO placeholder method while transitioning to non-db implementation
     *
     * Throw exception message in the format "null passed for 'type'!"
     */
    public void throwExceptionIfNull(Object object, Class<?> clazz) {
        throwExceptionIfNull(object, clazz.getName());
    }

    /**
    * TODO placeholder method while transitioning to non-db implementation
    *
    * Throw exception message in the format "null passed for 'type'!"
    */
   public void throwExceptionIfNull(Object object, String type) {
       if (object == null) {
           String msg = String.format("null passed for %s !", type);
           LOG.error(msg);
           throw new IllegalArgumentException(msg);
       }
   }

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public void throwExceptionIfNullElement(Object element, String type) {
        if (element == null) {
            String msg = String.format("null passed for %s !", type);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public void throwExceptionIfNullElementAndId(Element element, String type) {
        if (element == null || element.getElementId() == null) {
            String msg = String.format("null passed for %s !", type);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Throw exception message in the format "Cannot find type by id: id!"
     */
    public void throwExceptionIfCannotFindById(Object element, String type, String id) {
        if (element == null) {
            String msg = String.format("Cannot find %s by id: %s!", type, id);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
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
}
