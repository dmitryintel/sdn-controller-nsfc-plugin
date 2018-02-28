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

import org.osc.sdk.controller.element.InspectionPortElement;

/**
 * Translates to a port pair in SFC
 */
public class PortPairElement implements InspectionPortElement {

    //Port pair id
    private String elementId;

    private PortPairGroupElement portPairGroup;

    private NetworkElementImpl ingressPort;

    private NetworkElementImpl egressPort;

    public PortPairElement() {
    }

    public PortPairElement(String elementId, PortPairGroupElement portPairGroup, NetworkElementImpl ingress, NetworkElementImpl egress) {
        this.elementId = elementId;
        this.portPairGroup = portPairGroup;
        this.ingressPort = ingress;
        this.egressPort = egress;
    }

    @Override
    public String getElementId() {
        return this.elementId;
    }

    @Override
    public NetworkElementImpl getIngressPort() {
        return this.ingressPort;
    }

    public void setIngressPort(NetworkElementImpl ingressPort) {
        this.ingressPort = ingressPort;
    }

    @Override
    public NetworkElementImpl getEgressPort() {
        return this.egressPort;
    }

    public void setEgressPort(NetworkElementImpl egressPort) {
        this.egressPort = egressPort;
    }

    public PortPairGroupElement getPortPairGroup() {
        return this.portPairGroup;
    }

    public void setPortPairGroup(PortPairGroupElement portPairGroup) {
        this.portPairGroup = portPairGroup;
    }

    @Override
    public String getParentId() {
        return this.portPairGroup == null ? null : this.portPairGroup.getElementId();
    }

    @Override
    public String toString() {
        // use get elementid on ppg to avoid cyclic dependency and stackoverflow issues
        return "PortPairElement [elementId=" + this.elementId + ", portPairGroup=" + getParentId()
                + ", ingressPort=" + this.ingressPort + ", egressPort=" + this.egressPort + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.elementId == null) ? 0 : this.elementId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PortPairElement other = (PortPairElement) obj;
        if (this.elementId == null) {
            if (other.elementId != null) {
                return false;
            }
        } else if (!this.elementId.equals(other.elementId)) {
            return false;
        }
        return true;
    }

}
