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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.hippoecm.hst.configuration.components.HstComponentConfiguration;
import org.hippoecm.hst.core.container.ComponentManager;
import org.hippoecm.hst.core.container.ComponentManagerAware;
import org.hippoecm.hst.core.linking.DocumentParamsScanner;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyContext;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class PageCopyEventListener implements ComponentManagerAware {

    private static final Logger log = LoggerFactory.getLogger(PageCopyEventListener.class);

    private static final String DOCUMENT_MANAGEMENT_SERVICE_NAME = "org.onehippo.forge.channelmanager.pagesupport.document.management:type=DocumentManagementServiceMXBean";

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
    public void onPageCopyEvent(PageCopyEvent pageCopyEvent) {
        log.debug("##### onPageCopyEvent");

        if (pageCopyEvent.getException() != null) {
            return;
        }

        try {
            final PageCopyContext pageCopyContext = pageCopyEvent.getPageCopyContext();

            final Set<String> documentPathSet = getDocumentPathSetInPage(pageCopyContext.getSourcePage());
            final String sourceContentBasePath = pageCopyContext.getEditingMount().getContentPath();
            final String targetContentBasePath = pageCopyContext.getTargetMount().getContentPath();

            log.debug("##### sourceContentBasePath: {}", sourceContentBasePath);
            log.debug("##### targetContentBasePath: {}", targetContentBasePath);
            log.debug("##### documentPaths: {}", documentPathSet);
        } catch (Exception e) {
            log.error("Failed to invoke the document management service.", e);
        }
    }

    private Set<String> getDocumentPathSetInPage(final HstComponentConfiguration pageConfig) {
        List<String> documentPathList = DocumentParamsScanner.findDocumentPathsRecursive(pageConfig, Thread.currentThread().getContextClassLoader());
        return new LinkedHashSet<String>(documentPathList);
    }

    private Object invokeDocumentManagementServiceMBean(String operationName, Object[] params, String[] signature)
            throws Exception {
        final ObjectName mbeanName = new ObjectName(DOCUMENT_MANAGEMENT_SERVICE_NAME);
        final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

        if (!mbeanServer.isRegistered(mbeanName)) {
            throw new IllegalStateException("Document Management Service not available.");
        }

        return mbeanServer.invoke(mbeanName, operationName, params, signature);
    }
}
