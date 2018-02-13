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
import static org.osc.controller.nsfc.TestData.*;

import javax.persistence.EntityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.Mock;
import org.openstack4j.api.Builders;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;

public abstract class AbstractNeutronSfcPluginTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    protected TestTransactionControl txControl;

    protected EntityManager em;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        this.em = InMemDB.getEntityManager();
        this.txControl.init(this.em);
        setupDataObjects();
    }

    @After
    public void tearDown() throws Exception {
        InMemDB.close();
    }

    protected InspectionHookEntity persistInspectionHook() {
        persistInspectionPort();
        return this.txControl.required(() -> {
            sfc.getPortPairGroups().add(ppg);
            this.em.persist(sfc);

            ppg.setServiceFunctionChain(sfc);
            this.em.merge(ppg);

            this.em.persist(inspected);

            this.em.persist(inspectionHook);

            return inspectionHook;
        });
    }

    protected InspectionPortEntity persistInspectionPort() {

        portPair = portPairService.create(portPair);
        portPairGroup = Builders.portPairGroup()
                .portPairs(singletonList(portPair.getId()))
                .build();
        portPairGroup = portPairGroupService.create(portPairGroup);

        return this.txControl.required(() -> {
            this.em.persist(ppg);

            inspectionPort.setPortPairGroup(ppg);
            this.em.persist(inspectionPort);

            ppg.getPortPairs().add(inspectionPort);

            this.em.merge(ppg);
            return inspectionPort;
        });
    }
}
