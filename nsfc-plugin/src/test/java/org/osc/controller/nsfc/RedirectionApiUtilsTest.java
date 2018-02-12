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

import static org.junit.Assert.*;
import static org.osc.controller.nsfc.TestData.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.controller.nsfc.utils.RedirectionApiUtils;

@RunWith(MockitoJUnitRunner.class)
public class RedirectionApiUtilsTest extends AbstractNeutronSfcPluginTest {

    private RedirectionApiUtils utils;

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        this.utils = new RedirectionApiUtils(this.em, this.txControl, null);
    }

    @Test
    public void testUtils_InspectionPortByNetworkElements_Succeeds() throws Exception {
        // Arrange.
        persistInspectionPort();

        // Act/
        InspectionPortEntity foundPort = this.utils.findInspectionPortByNetworkElements(ingress, egress);

        // Assert.
        assertNotNull(foundPort);
        assertEquals(inspectionPort.getElementId(), foundPort.getElementId());
    }

    @Test
    public void testUtils_InspHookByInspectedAndPort_Succeeds() throws Exception {
        // Arrange
        persistInspectionHook();

        // Act.
        InspectionHookEntity foundIH = this.txControl.required(() -> {
            ServiceFunctionChainEntity tmpSfc = this.em.find(ServiceFunctionChainEntity.class,
                    sfc.getElementId());

            InspectionHookEntity ihe = this.utils.findInspHookByInspectedAndPort(inspected, tmpSfc);

            assertNotNull(ihe);
            assertEquals(inspectionHook.getHookId(), ihe.getHookId());
            return ihe;
        });

        // Assert.
        assertEquals(foundIH.getHookId(), inspectionHook.getHookId());
        assertEquals(foundIH.getServiceFunctionChain().getElementId(), sfc.getElementId());
        assertEquals(foundIH.getInspectedPort().getElementId(), inspected.getElementId());

    }

    @Test
    public void testUtils_RemoveSingleInspectionHook_Succeeds() throws Exception {
        // Arrange.
        persistInspectionHook();

        // Act.
        this.utils.removeSingleInspectionHook(inspectionHook.getHookId());

        // Assert.
        InspectionHookEntity inspectionHookEntity = this.txControl.required(() -> {
            InspectionHookEntity tmpInspectionHook = this.em.find(InspectionHookEntity.class, inspectionHook.getHookId());
            return tmpInspectionHook;
        });

        assertNull(inspectionHookEntity);
    }
}
