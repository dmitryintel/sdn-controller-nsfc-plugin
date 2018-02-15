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

import static org.osc.controller.nsfc.TestData.*;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.api.client.IOSClientBuilder.V3;
import org.openstack4j.api.networking.NetworkingService;
import org.openstack4j.api.networking.ext.ServiceFunctionChainService;

public abstract class AbstractNeutronSfcPluginTest {
    @Mock
    protected V3 v3;

    @Mock
    protected OSClientV3 osClient;

    @Mock
    protected ServiceFunctionChainService sfcService;

    @Mock
    protected NetworkingService networkingService;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        setupDataObjects();
        Mockito.when(this.networkingService.port()).thenReturn(portService);
        Mockito.when(this.sfcService.portchains()).thenReturn(portChainService);
        Mockito.when(this.sfcService.portpairs()).thenReturn(portPairService);
        Mockito.when(this.sfcService.portpairgroups()).thenReturn(portPairGroupService);
        Mockito.when(this.sfcService.flowclassifiers()).thenReturn(flowClassifierService);
        Mockito.when(this.osClient.sfc()).thenReturn(this.sfcService);
        Mockito.when(this.osClient.networking()).thenReturn(this.networkingService);
    }

    @After
    public void tearDown() throws Exception {
    }

    protected void persistIngress() {
        ingressPort = portService.create(Builders.port().macAddress(ingressPortEntity.getMacAddresses().get(0))
                                 .fixedIp(ingressPortEntity.getPortIPs().get(0), "mySubnet").build());
        ingressPortEntity.setElementId(ingressPort.getId());
    }

    protected void persistEgress() {
        egressPort = portService.create(Builders.port().macAddress(egressPortEntity.getMacAddresses().get(0))
                                .fixedIp(egressPortEntity.getPortIPs().get(0), "mySubnet").build());
        egressPortEntity.setElementId(egressPort.getId());
    }

    protected void persistInspectionPort(boolean withIngress, boolean withEgress) {
        if (withIngress) {
            portPair = portPair.toBuilder().ingressId(ingressPort.getId()).build();
        }

        if (withEgress) {
            portPair = portPair.toBuilder().egressId(egressPort.getId()).build();
        }

        portPair = portPairService.create(portPair);
    }

    protected void persistPortPairGroup() {
        portPairGroup = Builders.portPairGroup()
                .portPairs(new ArrayList<>(Arrays.asList(portPair.getId())))
                .build();
        portPairGroup = portPairGroupService.create(portPairGroup);
    }
}
