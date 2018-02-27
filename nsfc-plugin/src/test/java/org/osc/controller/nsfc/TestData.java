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
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.openstack4j.api.Builders;
import org.openstack4j.api.networking.PortService;
import org.openstack4j.api.networking.ext.FlowClassifierService;
import org.openstack4j.api.networking.ext.PortChainService;
import org.openstack4j.api.networking.ext.PortPairGroupService;
import org.openstack4j.api.networking.ext.PortPairService;
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
import org.osc.controller.nsfc.entities.ServiceFunctionChainElement;

class TestData {

    private static final String EADDR1_STR = "192.168.0.11";

    private static final String IADDR1_STR = "10.4.3.1";

    private static final String EMAC2_STR = "ee:ff:aa:bb:cc:02";

    private static final String EMAC1_STR = "ee:ff:aa:bb:cc:01";

    private static final String IMAC1_STR = "ff:ff:aa:bb:cc:01";

    private static final String INSPMAC1_STR = "aa:aa:aa:bb:cc:01";

    private static final String INSPADDR_STR = "10.4.5.6";

    private static final Random ID_GENERATOR = new Random(System.currentTimeMillis());

    public static InspectionHookEntity inspectionHook;
    public static InspectionPortEntity inspectionPort;
    public static PortPairGroupEntity ppgEntity;
    public static ServiceFunctionChainElement sfc;

    public static NetworkElementEntity ingressPortEntity;
    public static NetworkElementEntity egressPortEntity;
    public static NetworkElementEntity inspected;

    public static PortChain portChain;
    public static PortPair portPair;
    public static PortPairGroup portPairGroup;
    public static Port ingressPort;
    public static Port egressPort;

    public static PortService portService;
    public static PortChainService portChainService;
    public static PortPairService portPairService;
    public static PortPairGroupService portPairGroupService;
    public static FlowClassifierService flowClassifierService;

    @SuppressWarnings("unchecked")
    public static void setupDataObjects() {
        ingressPortEntity = new NetworkElementEntity();
        ingressPortEntity.setElementId(IMAC1_STR + IMAC1_STR);
        ingressPortEntity.setMacAddresses(asList(IMAC1_STR));
        ingressPortEntity.setPortIPs(asList(IADDR1_STR));

        egressPortEntity = new NetworkElementEntity();
        egressPortEntity.setElementId(EMAC1_STR + EMAC1_STR);
        egressPortEntity.setMacAddresses(asList(EMAC1_STR));
        egressPortEntity.setPortIPs(asList(EADDR1_STR));

        inspected = new NetworkElementEntity();
        inspected.setElementId("iNsPeCtEdPoRt");
        inspected.setMacAddresses(asList(INSPMAC1_STR));
        inspected.setPortIPs(asList(INSPADDR_STR));

        ppgEntity = new PortPairGroupEntity();

        inspectionPort = new InspectionPortEntity();
        inspectionPort.setIngressPort(ingressPortEntity);
        inspectionPort.setEgressPort(egressPortEntity);

        sfc = new ServiceFunctionChainElement();

        inspectionHook = new InspectionHookEntity(inspected, sfc);

        portChain = Builders.portChain().build();
        portPair = Builders.portPair().build();
        portPairGroup = Builders.portPairGroup().build();

        portService = new MockPortService();
        portChainService = new MockPortChainService();
        portPairService = new MockPortPairService();
        portPairGroupService = new MockPortPairGroupService();
        flowClassifierService = new MockFlowClassifierService();
    }

    private static class CRUDMockService<T extends org.openstack4j.model.common.Resource> {
        Map<String, T> dataObjects = new HashMap<String, T>();

        public List<? extends T> list()  {
            return new ArrayList<T>(this.dataObjects.values());
        }

        public T get(String id) {
            if (id == null) {
                throw new IllegalArgumentException("id cannot be null");
            }
            return this.dataObjects.get(id);
        }

        public T create(T object) {
            String id;
            do {
				id = ID_GENERATOR.nextLong() + "";
			} while (this.dataObjects.keySet().contains(id));

            object.setId(id);
            this.dataObjects.put(id, object);
            return object;
        }

        public T update(String id, T object) {
            if (id == null) {
                throw new IllegalArgumentException("id cannot be null");
            }

            object.setId(id);
            this.dataObjects.put(id, object);

            return object;
        }

        public ActionResponse delete(String id) {
            if (id == null) {
                throw new IllegalArgumentException("id cannot be null");
            }
            this.dataObjects.remove(id);
            return ActionResponse.actionSuccess();
        }
    }

    private static class MockPortService extends CRUDMockService<Port> implements PortService {

        @Override
        public List<? extends Port> list(PortListOptions options) {
            return list();
        }

        @Override
        public List<? extends Port> create(List<? extends Port> ports) {
            if (ports == null) {
                throw new IllegalArgumentException("List of ports cannot be null");
            }
            return ports.stream().map(p -> create(p)).collect(toList());
        }

        @Override
        public Port update(Port object) {
            return update(object.getId(), object);
        }

    }
    private static class MockPortChainService extends CRUDMockService<PortChain> implements PortChainService {
    }
    private static class MockPortPairGroupService extends CRUDMockService<PortPairGroup> implements PortPairGroupService {
    }
    private static class MockPortPairService extends CRUDMockService<PortPair> implements PortPairService {
    }
    private static class MockFlowClassifierService extends CRUDMockService<FlowClassifier> implements FlowClassifierService {
    }

}
