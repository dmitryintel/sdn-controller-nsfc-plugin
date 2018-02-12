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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;
import org.osc.sdk.controller.element.NetworkElement;

@Entity
@Table(name = "PORT_PAIR_GROUP")
public class PortPairGroupEntity implements NetworkElement {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "element_id", unique = true)
    private String elementId;

    @OneToMany(mappedBy = "portPairGroup", fetch = FetchType.EAGER)
    private List<InspectionPortEntity> portPairs = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sfc_fk", foreignKey = @ForeignKey(name = "FK_PPG_SFC"))
    private ServiceFunctionChainEntity serviceFunctionChain;

    public PortPairGroupEntity() {
    }

    public PortPairGroupEntity(String elementId) {
        this.elementId = elementId;
    }

    @Override
    public String getElementId() {
        return this.elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public ServiceFunctionChainEntity getServiceFunctionChain() {
        return this.serviceFunctionChain;
    }

    public void setServiceFunctionChain(ServiceFunctionChainEntity serviceFunctionChain) {
        this.serviceFunctionChain = serviceFunctionChain;
    }

    public List<InspectionPortEntity> getPortPairs() {
        return this.portPairs;
    }

    @Override
    public String toString() {
        // use get elementid on sfc to avoid cyclic dependency and stackoverflow issues
        return "PortPairGroupEntity [elementId=" + this.elementId + ", portPairs=" + this.portPairs
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
