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

import java.util.List;

import org.osc.sdk.controller.element.NetworkElement;

public class NetworkElementImpl implements NetworkElement {

    private String elementId;

    // TODO : for SFC functionality
    private String parentId;

    private List<String> macAddresses;

    private List<String> portIPs;

    private FlowClassifierElement inspectionHook;

    public NetworkElementImpl() {
    }

    public NetworkElementImpl(String elementId, List<String> macAddresses, List<String> portIps,
            String parentId) {
        super();
        this.elementId = elementId;
        this.parentId = parentId;
        this.macAddresses = macAddresses;
        this.portIPs = portIps;
    }

    @Override
    public String getElementId() {
        return this.elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    @Override
    public List<String> getPortIPs() {
        return this.portIPs;
    }

    public void setPortIPs(List<String> portIPs) {
        this.portIPs = portIPs;
    }

    @Override
    public List<String> getMacAddresses() {
        return this.macAddresses;
    }

    public void setMacAddresses(List<String> macAddresses) {
        this.macAddresses = macAddresses;
    }

    @Override
    public String getParentId() {
        return this.parentId;
    }

    @Override
    public String toString() {
        return "NetworkElementImpl [elementId=" + this.elementId + ", parentId=" + this.parentId + ", macAddresses="
                + this.macAddresses + ", portIPs=" + this.portIPs + "]";
    }
}
