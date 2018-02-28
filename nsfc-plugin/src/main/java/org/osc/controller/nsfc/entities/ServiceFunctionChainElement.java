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
package org.osc.controller.nsfc.entities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osc.sdk.controller.element.InspectionPortElement;
import org.osc.sdk.controller.element.NetworkElement;

public class ServiceFunctionChainElement implements NetworkElement, InspectionPortElement {

    private String elementId;

    private List<PortPairGroupElement> portPairGroups = new ArrayList<>();

    private Set<FlowClassifierElement> inspectionHooks = new HashSet<>();

    public ServiceFunctionChainElement() {
    }

    public ServiceFunctionChainElement(String elementId) {
        super();
        this.elementId = elementId;
    }

    @Override
    public String getElementId() {
        return this.elementId;
    }

    public List<PortPairGroupElement> getPortPairGroups() {
        return this.portPairGroups;
    }

    public void setPortPairGroups(List<PortPairGroupElement> portPairGroups) {
        this.portPairGroups = portPairGroups;
    }

    public Set<FlowClassifierElement> getInspectionHooks() {
        return this.inspectionHooks;
    }

    @Override
    public String toString() {
        return "ServiceFunctionChainElement [elementId=" + this.elementId + ", portPairGroups=" + this.portPairGroups + "]";
    }

    @Override
    public String getParentId() {
        return null;
    }

    @Override
    public List<String> getMacAddresses() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getPortIPs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NetworkElement getIngressPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NetworkElement getEgressPort() {
        throw new UnsupportedOperationException();
    }

}
