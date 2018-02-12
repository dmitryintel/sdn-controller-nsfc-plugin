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

import static javax.persistence.FetchType.EAGER;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;
import org.osc.sdk.controller.element.InspectionPortElement;
import org.osc.sdk.controller.element.NetworkElement;

@Entity
@Table(name = "SERVICE_FUNCTION_CHAIN")
public class ServiceFunctionChainEntity implements NetworkElement, InspectionPortElement {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "element_id", unique = true)
    private String elementId;

    @OneToMany(mappedBy = "serviceFunctionChain", fetch = FetchType.EAGER)
    @OrderColumn(name = "ppg_order")
    private List<PortPairGroupEntity> portPairGroups = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = false, fetch = EAGER, mappedBy="serviceFunctionChain")
    private Set<InspectionHookEntity> inspectionHooks = new HashSet<>();

    public ServiceFunctionChainEntity() {
    }

    public ServiceFunctionChainEntity(String elementId) {
        super();
        this.elementId = elementId;
    }

    @Override
    public String getElementId() {
        return this.elementId;
    }

    public List<PortPairGroupEntity> getPortPairGroups() {
        return this.portPairGroups;
    }

    public void setPortPairGroups(List<PortPairGroupEntity> portPairGroups) {
        this.portPairGroups = portPairGroups;
    }

    public Set<InspectionHookEntity> getInspectionHooks() {
        return this.inspectionHooks;
    }

    @Override
    public String toString() {
        return "ServiceFunctionChainEntity [elementId=" + this.elementId + ", portPairGroups=" + this.portPairGroups + "]";
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
