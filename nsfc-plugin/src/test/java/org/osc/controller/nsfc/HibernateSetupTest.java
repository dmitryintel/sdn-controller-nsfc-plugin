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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.osc.controller.nsfc.TestData.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.sdk.controller.element.InspectionHookElement;

@RunWith(MockitoJUnitRunner.class)
public class HibernateSetupTest extends AbstractNeutronSfcPluginTest {

    @Test
    public void testDb_PersistInspectionPort_verifyCorrectNumberOfMacsAdPortIps() throws Exception {
        // Arrange.
        persistInspectionPort();

        // Act.
        InspectionPortEntity tmp = this.txControl.requiresNew(() -> {
            return this.em.find(InspectionPortEntity.class, inspectionPort.getElementId());
        });

        // Assert.
        assertEquals(2, tmp.getEgressPort().getMacAddresses().size());
        assertEquals(2, tmp.getEgressPort().getPortIPs().size());
        assertEquals(2, tmp.getIngressPort().getMacAddresses().size());
        assertEquals(2, tmp.getIngressPort().getPortIPs().size());
    }

    @Test
    public void testDb_GetInspectionHook_Succeeds() throws Exception {
        // Arrange.
        persistInspectionHook();

        // Act.
        InspectionHookElement inspectionHookElement = this.txControl.required(() -> {
            InspectionHookEntity tmp = this.em.find(InspectionHookEntity.class, inspectionHook.getHookId());
            assertNotNull(tmp);
            assertEquals(tmp.getServiceFunctionChain().getElementId(), sfc.getElementId());
            return tmp;
        });

        // Assert.
        // Here we are mostly afraid of LazyInitializationException
        assertNotNull(inspectionHookElement);
        assertNotNull(inspectionHookElement.getHookId());
        assertEquals(inspectionHookElement.getInspectedPort().getElementId(), inspected.getElementId());
    }
}
