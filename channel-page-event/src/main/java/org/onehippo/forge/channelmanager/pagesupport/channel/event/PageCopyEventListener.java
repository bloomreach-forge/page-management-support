/*
 * Copyright 2015 Hippo B.V. (http://www.onehippo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onehippo.forge.channelmanager.pagesupport.channel.event;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.hippoecm.hst.core.container.ComponentManager;
import org.hippoecm.hst.core.container.ComponentManagerAware;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyEvent;
import org.onehippo.cms7.services.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;

public class PageCopyEventListener implements ComponentManagerAware {

    private static final Logger log = LoggerFactory.getLogger(PageCopyEventListener.class);

    private static final String DOCUMENT_MANAGEMENT_SERVICE_NAME = "org.onehippo.forge.channelmanager.pagesupport.document.management:type=DocumentManagementServiceMBean";

    private ComponentManager componentManager;

    @Override
    public void setComponentManager(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    public void init() {
        componentManager.registerEventSubscriber(this);
    }

    public void destroy() {
        componentManager.unregisterEventSubscriber(this);
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onPageCopyEvent(PageCopyEvent event) {
        if (event.getException() != null) {
            return;
        }

        log.info("Show an example of copying a content document as well!!");

        // copying a document that was selected by a component. For this in this example we will use
        // 1: hst workflow support
        // 2: org.hippoecm.hst.core.linking.DocumentParamsScanner.findDocumentPathsRecursive to find which parameter names refer
        // to documents

        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

            Object [] params = new Object [] { "/a/b/c", "/a/b", "c2" };
            String [] signature = new String [] { String.class.getName(), String.class.getName(), String.class.getName() };

            invokeDocumentManagementServiceMBean(mbeanServer, "copyDocument", params, signature);
        } catch (Exception e) {
            log.error("Failed to invoke the document management service MBean.", e);
        }
    }

    private Object invokeDocumentManagementServiceMBean(final MBeanServer mbeanServer, String operationName,
            Object[] params, String[] signature) throws Exception {
        final ObjectName mbeanName = new ObjectName(DOCUMENT_MANAGEMENT_SERVICE_NAME);

        if (!mbeanServer.isRegistered(mbeanName)) {
            throw new IllegalStateException("Document Management Service MBean not available.");
        }

        return mbeanServer.invoke(mbeanName, operationName, params, signature);
    }
}
