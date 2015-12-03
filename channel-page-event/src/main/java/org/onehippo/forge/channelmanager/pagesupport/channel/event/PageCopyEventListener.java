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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.commons.lang.StringUtils;
import org.hippoecm.hst.configuration.components.HstComponentConfiguration;
import org.hippoecm.hst.configuration.hosting.Mount;
import org.hippoecm.hst.core.container.ComponentManager;
import org.hippoecm.hst.core.container.ComponentManagerAware;
import org.hippoecm.hst.core.linking.DocumentParamsScanner;
import org.hippoecm.hst.core.request.HstRequestContext;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyContext;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyEvent;
import org.hippoecm.repository.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class PageCopyEventListener implements ComponentManagerAware {

    private static final Logger log = LoggerFactory.getLogger(PageCopyEventListener.class);

    private ComponentManager componentManager;

    private DocumentManagementServiceClient documentManagementServiceClient;

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

    public DocumentManagementServiceClient getDocumentManagementServiceClient() {
        if (documentManagementServiceClient == null) {
            documentManagementServiceClient = new DocumentManagementServiceClient();
        }

        return documentManagementServiceClient;
    }

    public void setDocumentManagementServiceClient(DocumentManagementServiceClient documentManagementServiceClient) {
        this.documentManagementServiceClient = documentManagementServiceClient;
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onPageCopyEvent(PageCopyEvent pageCopyEvent) {
        log.debug("##### onPageCopyEvent");

        if (pageCopyEvent.getException() != null) {
            return;
        }

        final PageCopyContext pageCopyContext = pageCopyEvent.getPageCopyContext();

        try {
            final Mount sourceMount = pageCopyContext.getEditingMount();
            final Mount targetMount = pageCopyContext.getTargetMount();

            if (!StringUtils.equals(sourceMount.getContentPath(), targetMount.getContentPath())) {
                copyDocumentsLinkedByHstComponentConfiguration(pageCopyContext.getRequestContext(),
                        pageCopyContext.getSourcePage(), sourceMount, targetMount);
            }
        } catch (RuntimeException e) {
            pageCopyEvent.setException(e);
        } catch (Exception e) {
            pageCopyEvent.setException(
                    new RuntimeException("Failed to handle page copy event properly. " + e.toString(), e));
        }
    }

    private void copyDocumentsLinkedByHstComponentConfiguration(final HstRequestContext requestContext,
            final HstComponentConfiguration compConfig, final Mount sourceMount, final Mount targetMount) {
        final String sourceContentBasePath = sourceMount.getContentPath();
        final String targetContentBasePath = targetMount.getContentPath();

        log.debug("##### sourceContentBasePath: {}", sourceContentBasePath);
        log.debug("##### targetContentBasePath: {}", targetContentBasePath);

        try {
            final Node sourceContentBaseNode = requestContext.getSession().getNode(sourceContentBasePath);
            final Node targetContentBaseNode = requestContext.getSession().getNode(targetContentBasePath);
            String targetTranslationLanguage = JcrUtils.getStringProperty(targetContentBaseNode,
                    "hippotranslation:locale", null);

            if (StringUtils.isBlank(targetTranslationLanguage)) {
                log.error("Target translation language is blank at '{}'.", targetContentBasePath);
                return;
            }

            final Set<String> documentPathSet = getDocumentPathSetInPage(compConfig);
            log.debug("##### documentPaths: {}", documentPathSet);

            String sourceDocumentAbsPath;
            String sourceFolderRelPath;
            String targetDocumentAbsPath;
            String targetFolderAbsPath;
            String documentNodeName;

            for (String documentPath : documentPathSet) {
                if (StringUtils.startsWith(documentPath, "/")) {
                    log.info(
                            "##### skiping '{}' because it's an absolute jcr path, not relative to source mount content base",
                            documentPath);
                    continue;
                }

                sourceDocumentAbsPath = sourceContentBasePath + "/" + documentPath;
                int offset = documentPath.lastIndexOf('/');

                if (offset != -1) {
                    sourceFolderRelPath = documentPath.substring(0, offset);
                    documentNodeName = documentPath.substring(offset + 1);
                } else {
                    sourceFolderRelPath = null;
                    documentNodeName = documentPath;
                }

                if (!HippoFolderDocumentUtils.documentExists(requestContext.getSession(), sourceDocumentAbsPath)) {
                    log.info("##### skiping '{}' because it doesn't exist under '{}'.", documentPath,
                            sourceContentBasePath);
                    continue;
                }

                targetDocumentAbsPath = targetContentBasePath + "/" + documentPath;

                if (HippoFolderDocumentUtils.documentExists(requestContext.getSession(), targetDocumentAbsPath)) {
                    log.info("##### skiping '{}' because it already exists under '{}'.", documentPath,
                            targetContentBasePath);
                    continue;
                }

                targetFolderAbsPath = StringUtils.substringBeforeLast(targetDocumentAbsPath, "/");

                if (!HippoFolderDocumentUtils.folderExists(requestContext.getSession(), targetFolderAbsPath)) {
                    translateFolders(requestContext.getSession(), sourceContentBaseNode, sourceFolderRelPath,
                            targetContentBaseNode, targetTranslationLanguage);
                }

                getDocumentManagementServiceClient().translateDocument(sourceDocumentAbsPath, targetTranslationLanguage,
                        documentNodeName);
            }
        } catch (Exception e) {
            log.error("Failed to invoke the document management service.", e);
            throw new RuntimeException("Failed to copy all the linked documents. " + e.toString());
        }
    }

    /**
     * In case of targeting, you also get all the locations for the
     * variants in the list. The returned set can have values that start
     * with a '/' or without. If they start with a '/', they are absolute
     * paths (from jcr root). If they don't start with a '/', they are
     * relative to the channel content root.
     * @param pageConfig
     * @return
     */
    private Set<String> getDocumentPathSetInPage(final HstComponentConfiguration pageConfig) {
        List<String> documentPathList = DocumentParamsScanner.findDocumentPathsRecursive(pageConfig,
                Thread.currentThread().getContextClassLoader());
        return new LinkedHashSet<String>(documentPathList);
    }

    private void translateFolders(final Session session, final Node sourceBaseFolderNode,
            final String sourceFolderRelPath, final Node targetBaseFolderNode, final String targetTranslationLanguage)
                    throws Exception {
        String[] folderNodeNames = StringUtils.split(sourceFolderRelPath, "/");
        String sourceFolderLocation = sourceBaseFolderNode.getPath();
        String targetFolderLocation = targetBaseFolderNode.getPath();

        for (String folderNodeName : folderNodeNames) {
            sourceFolderLocation += "/" + folderNodeName;

            if (!HippoFolderDocumentUtils.folderExists(session, sourceFolderLocation)) {
                throw new IllegalArgumentException("Source folder doesn't exist at '" + sourceFolderLocation + "'.");
            }

            targetFolderLocation += "/" + folderNodeName;

            if (!HippoFolderDocumentUtils.folderExists(session, targetFolderLocation)) {
                getDocumentManagementServiceClient().translateFolder(sourceFolderLocation, targetTranslationLanguage,
                        folderNodeName);
            }
        }
    }

}
