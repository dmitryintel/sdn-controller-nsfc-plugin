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
