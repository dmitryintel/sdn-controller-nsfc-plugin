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
import java.util.List;

import org.osc.sdk.controller.element.NetworkElement;

public class PortPairGroupElement implements NetworkElement {

    private String elementId;

    private List<PortPairElement> portPairs = new ArrayList<>();

    private ServiceFunctionChainElement serviceFunctionChain;

    public PortPairGroupElement() {
    }

    public PortPairGroupElement(String elementId) {
        this.elementId = elementId;
    }

    @Override
    public String getElementId() {
        return this.elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public ServiceFunctionChainElement getServiceFunctionChain() {
        return this.serviceFunctionChain;
    }

    public void setServiceFunctionChain(ServiceFunctionChainElement serviceFunctionChain) {
        this.serviceFunctionChain = serviceFunctionChain;
    }

    public List<PortPairElement> getPortPairs() {
        return this.portPairs;
    }

    @Override
    public String toString() {
        // use get elementid on sfc to avoid cyclic dependency and stackoverflow issues
        return "PortPairGroupElement [elementId=" + this.elementId + ", portPairs=" + this.portPairs
                + ", serviceFunctionChain=" + getParentId() + "]";
    }

    @Override
    public String getParentId() {
        return this.serviceFunctionChain == null ? null : this.serviceFunctionChain.getElementId();
    }

    @Override
    public List<String> getMacAddresses() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getPortIPs() {
        throw new UnsupportedOperationException();
    }

}
