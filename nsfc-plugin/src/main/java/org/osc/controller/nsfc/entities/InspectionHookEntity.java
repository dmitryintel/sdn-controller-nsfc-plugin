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

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;
import org.osc.sdk.controller.FailurePolicyType;
import org.osc.sdk.controller.TagEncapsulationType;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.InspectionPortElement;

@Entity
@Table(name = "INSPECTION_HOOK")
public class InspectionHookEntity implements InspectionHookElement {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "hook_id", unique = true)
    private String hookId;

    @OneToOne(fetch = EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "inspected_port_fk", nullable = false)
    private NetworkElementEntity inspectedPort;

    @ManyToOne(fetch = EAGER)
    @JoinColumn(name = "sfc_fk", nullable = false)
    private ServiceFunctionChainEntity serviceFunctionChain;

    InspectionHookEntity() {
    }

    public InspectionHookEntity(NetworkElementEntity inspectedPort, ServiceFunctionChainEntity serviceFunctionChain) {
        this.inspectedPort = inspectedPort;
        this.serviceFunctionChain = serviceFunctionChain;
    }

    @Override
    public String getHookId() {
        return this.hookId;
    }

    public void setHookId(String hookId) {
        this.hookId = hookId;
    }

    @Override
    public NetworkElementEntity getInspectedPort() {
        return this.inspectedPort;
    }

    public void setInspectedPort(NetworkElementEntity inspectedPort) {
        this.inspectedPort = inspectedPort;
    }

    public ServiceFunctionChainEntity getServiceFunctionChain() {
        return this.serviceFunctionChain;
    }

    public void setServiceFunctionChain(ServiceFunctionChainEntity serviceFunctionChain) {
        this.serviceFunctionChain = serviceFunctionChain;
    }

    @Override
    public Long getTag() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long getOrder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TagEncapsulationType getEncType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FailurePolicyType getFailurePolicyType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "InspectionHookEntity [hookId=" + this.hookId + ", inspectedPort=" + this.inspectedPort
                + ", serviceFunctionChain=" + this.serviceFunctionChain + "]";
    }

    @Override
    public InspectionPortElement getInspectionPort() {
        return this.serviceFunctionChain;
    }

}
