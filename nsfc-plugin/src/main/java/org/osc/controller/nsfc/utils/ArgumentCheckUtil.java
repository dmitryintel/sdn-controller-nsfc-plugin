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

import java.util.List;

import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.NetworkElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArgumentCheckUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ArgumentCheckUtil.class);

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public static void throwExceptionIfNullOrEmptyNetworkElementList(List<NetworkElement> neList, String type) {
        if (neList == null || neList.isEmpty()) {
            String msg = String.format("null passed for %s !", type);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public static void throwExceptionIfNullElementAndParentId(Element element, String type) {
       if (element == null || element.getParentId() == null) {
           String msg = String.format("null passed for %s !", type);
           LOG.error(msg);
           throw new IllegalArgumentException(msg);
       }
   }
}
