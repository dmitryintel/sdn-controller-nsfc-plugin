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

import org.osc.sdk.controller.FailurePolicyType;
import org.osc.sdk.controller.TagEncapsulationType;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.InspectionPortElement;

public class InspectionHookEntity implements InspectionHookElement {

    private String hookId;

    private NetworkElementEntity inspectedPort;

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
