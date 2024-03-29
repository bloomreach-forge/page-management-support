/*
 * Copyright 2024 Bloomreach (https://www.bloomreach.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onehippo.forge.channelmanager.pagesupport.document.management.impl;

import java.lang.management.ManagementFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.lang3.StringUtils;
import org.hippoecm.repository.util.JcrUtils;
import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.forge.channelmanager.pagesupport.document.management.DocumentManagementService;
import org.onehippo.forge.channelmanager.pagesupport.document.management.DocumentManagementServiceMXBean;
import org.onehippo.repository.modules.AbstractReconfigurableDaemonModule;
import org.onehippo.repository.modules.DaemonModule;
import org.onehippo.repository.modules.ProvidesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hippo Repository {@link DaemonModule} implementation
 * to register/unregister {@link DocumentManagementService} component.
 */
@ProvidesService(types = DocumentManagementService.class)
public class DocumentManagementServiceDaemonModule extends AbstractReconfigurableDaemonModule {

    private static final Logger log = LoggerFactory.getLogger(DocumentManagementServiceDaemonModule.class);

    public static final String DOCUMENT_MANAGEMENT_SERVICE_PARAM = "document.management.service";

    private Session session;

    private String documentManagementServiceClassName;

    private DocumentManagementService documentManagementService;

    @Override
    protected void doConfigure(Node moduleConfig) throws RepositoryException {
        if (moduleConfig.hasProperty(DOCUMENT_MANAGEMENT_SERVICE_PARAM)) {
            documentManagementServiceClassName = StringUtils
                    .trim(JcrUtils.getStringProperty(moduleConfig, DOCUMENT_MANAGEMENT_SERVICE_PARAM, null));
        }
    }

    @Override
    protected void doInitialize(Session session) throws RepositoryException {
        this.session = session;

        documentManagementService = null;

        if (StringUtils.isEmpty(documentManagementServiceClassName)) {
            documentManagementService = new DocumentWorkflowDocumentManagementService();
        } else {
            try {
                documentManagementService = (DocumentManagementService) Class
                        .forName(documentManagementServiceClassName).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to create document management service.", e);
            }
        }

        if (documentManagementService != null) {
            documentManagementService.initialize(session);

            registerDocumentManagementServiceInHippoServiceRegistry();
            registerDocumentManagementServiceMBean();
        }
    }

    @Override
    protected void doShutdown() {
        if (documentManagementService != null) {
            unregisterDocumentManagementServiceInHippoServiceRegistry();
            unregisterDocumentManagementServiceMBean();

            documentManagementService.destroy();
            documentManagementService = null;
        }
    }

    @Override
    protected void onConfigurationChange(final Node moduleConfig) throws RepositoryException {
        super.onConfigurationChange(moduleConfig);

        doShutdown();
        doInitialize(session);
    }

    private void registerDocumentManagementServiceInHippoServiceRegistry() {
        HippoServiceRegistry.register(documentManagementService, DocumentManagementService.class);
    }

    private void unregisterDocumentManagementServiceInHippoServiceRegistry() {
        HippoServiceRegistry.unregister(documentManagementService, DocumentManagementService.class);
    }

    private void registerDocumentManagementServiceMBean() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName mbeanName = new ObjectName(DocumentManagementServiceMXBean.NAME);

            if (mbeanServer.isRegistered(mbeanName)) {
                mbeanServer.unregisterMBean(mbeanName);
            }

            mbeanServer.registerMBean(documentManagementService, mbeanName);
        } catch (Exception e) {
            log.error("Failed to register MBean.", e);
        }
    }

    private void unregisterDocumentManagementServiceMBean() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName mbeanName = new ObjectName(DocumentManagementServiceMXBean.NAME);

            if (mbeanServer.isRegistered(mbeanName)) {
                mbeanServer.unregisterMBean(mbeanName);
            }
        } catch (Exception e) {
            log.error("Failed to unregister MBean.", e);
        }
    }
}
