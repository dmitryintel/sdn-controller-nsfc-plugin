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
package org.osc.controller.nsfc.utils;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;

import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.openstack4j.model.network.options.PortListOptions;

public class OsCalls {

    private OSClientV3 osClient;

    public OsCalls(OSClientV3 osClient) {
        this.osClient = osClient;
    }

   public FlowClassifier createFlowClassifier(FlowClassifier flowClassifier) {
       checkArgument(flowClassifier != null, "null passed for %s !", "Flow Classifier");

       flowClassifier = flowClassifier.toBuilder().id(null).build();
       return this.osClient.sfc().flowclassifiers().create(flowClassifier);
   }

   public PortChain createPortChain(PortChain portChain) {
       checkArgument(portChain != null, "null passed for %s !", "Port Chain");
       portChain = portChain.toBuilder().id(null).build();
       portChain = this.osClient.sfc().portchains().create(portChain);

       return fixPortChainCollections(portChain);
   }

   public PortPairGroup createPortPairGroup(PortPairGroup portPairGroup) {
       checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");
       portPairGroup = portPairGroup.toBuilder().id(null).build();

       portPairGroup = this.osClient.sfc().portpairgroups().create(portPairGroup);
       return portPairGroup;
   }

   public PortPair createPortPair(PortPair portPair) {
       checkArgument(portPair != null, "null passed for %s !", "Port Pair");
       portPair = portPair.toBuilder().id(null).build();

       portPair = this.osClient.sfc().portpairs().create(portPair);
       return portPair;
   }

   public List<? extends FlowClassifier> listFlowClassifiers() {
       return this.osClient.sfc().flowclassifiers().list();
   }

   public List<? extends PortPairGroup> listPortPairGroups() {
       return this.osClient.sfc().portpairgroups().list();
   }

   public List<? extends PortPair> listPortPairs() {
       return this.osClient.sfc().portpairs().list();
   }

   public List<? extends PortChain> listPortChains() {
       return this.osClient.sfc().portchains().list();
   }

   public List<? extends Port> listPorts() {
       return this.osClient.networking().port().list();
   }

   public List<? extends Port> listPorts(PortListOptions options) {
       return this.osClient.networking().port().list(options);
   }

   public FlowClassifier getFlowClassifier(String flowClassifierId) {
       return this.osClient.sfc().flowclassifiers().get(flowClassifierId);
   }

   public PortChain getPortChain(String portChainId) {
       PortChain portChain = this.osClient.sfc().portchains().get(portChainId);
       return fixPortChainCollections(portChain);
   }

   public PortPairGroup getPortPairGroup(String portPairGroupId) {
       return this.osClient.sfc().portpairgroups().get(portPairGroupId);
   }

   public PortPair getPortPair(String portPairId) {
       return this.osClient.sfc().portpairs().get(portPairId);
   }

   public Port getPort(String portId) {
       return this.osClient.networking().port().get(portId);
   }

   public FlowClassifier updateFlowClassifier(String flowClassifierId, FlowClassifier flowClassifier) {
       checkArgument(flowClassifierId != null, "null passed for %s !", "Flow Classifier Id");
       checkArgument(flowClassifier != null, "null passed for %s !", "Flow Classifier");

       // OS won't let us modify some attributes. Must be null on update object
       flowClassifier = flowClassifier.toBuilder().id(null).projectId(null).build();

       return this.osClient.sfc().flowclassifiers().update(flowClassifierId, flowClassifier);
   }

   public PortChain updatePortChain(String portChainId, PortChain portChain) {
       checkArgument(portChainId != null, "null passed for %s !", "Port Chain Id");
       checkArgument(portChain != null, "null passed for %s !", "Port Chain");

       // OS won't let us modify some attributes. Must be null on update object
       portChain = portChain.toBuilder().id(null).projectId(null).chainParameters(null).chainId(null).build();

       portChain = this.osClient.sfc().portchains().update(portChainId, portChain);
       return fixPortChainCollections(portChain);
   }

   public PortPairGroup updatePortPairGroup(String portPairGroupId, PortPairGroup portPairGroup) {
       checkArgument(portPairGroupId != null, "null passed for %s !", "Port Pair Group Id");
       checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");

       // OS won't let us modify some attributes. Must be null on update object
       portPairGroup  = portPairGroup.toBuilder().id(null).projectId(null).portPairGroupParameters(null).build();

       portPairGroup = this.osClient.sfc().portpairgroups().update(portPairGroupId, portPairGroup);
       return portPairGroup;
   }

   public PortPair updatePortPair(String portPairId, PortPair portPair) {
       checkArgument(portPairId != null, "null passed for %s !", "Port Pair Id");
       checkArgument(portPair != null, "null passed for %s !", "Port Pair");

       // OS won't let us modify some attributes. Must be null on update object
       portPair = portPair.toBuilder().id(null).projectId(null).build();
       portPair = this.osClient.sfc().portpairs().update(portPairId, portPair);
       return portPair;
   }

   public Port updatePort(Port port) {
       return this.osClient.networking().port().update(port);
   }

   public ActionResponse deleteFlowClassifier(String flowClassifierId) {
       return this.osClient.sfc().flowclassifiers().delete(flowClassifierId);
   }

   public ActionResponse deletePortChain(String portChainId) {
       ActionResponse response = this.osClient.sfc().portchains().delete(portChainId);
       return response;
   }

   public ActionResponse deletePortPairGroup(String portPairGroupId) {
       ActionResponse response = this.osClient.sfc().portpairgroups().delete(portPairGroupId);
       return response;
   }

   public ActionResponse deletePortPair(String portPairId) {
       ActionResponse response = this.osClient.sfc().portpairs().delete(portPairId);
       return response;
   }

   private PortChain fixPortChainCollections(PortChain portChain) {
       if (portChain == null) {
           return null;
       }

       if (portChain.getFlowClassifiers() == null) {
           portChain = portChain.toBuilder().flowClassifiers(new ArrayList<>()).build();
       }

       if (portChain.getPortPairGroups() == null) {
           portChain = portChain.toBuilder().portPairGroups(new ArrayList<>()).build();
       }

       return portChain;
   }
}
