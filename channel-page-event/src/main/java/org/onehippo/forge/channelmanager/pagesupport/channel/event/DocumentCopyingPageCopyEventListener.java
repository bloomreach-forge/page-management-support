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
//import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyContext;
//import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class DocumentCopyingPageCopyEventListener implements ComponentManagerAware {

    private static final Logger log = LoggerFactory.getLogger(DocumentCopyingPageCopyEventListener.class);

    private ComponentManager componentManager;

    private DocumentManagementServiceClient documentManagementServiceClient;

    private boolean copyDocumentsLinkedBySourcePage;

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

    public boolean isCopyDocumentsLinkedBySourcePage() {
        return copyDocumentsLinkedBySourcePage;
    }

    public void setCopyDocumentsLinkedBySourcePage(boolean copyDocumentsLinkedBySourcePage) {
        this.copyDocumentsLinkedBySourcePage = copyDocumentsLinkedBySourcePage;
    }

    /*
    @Subscribe
    @AllowConcurrentEvents
    public void onPageCopyEvent(PageCopyEvent pageCopyEvent) {
        if (pageCopyEvent.getException() != null) {
            return;
        }

        final PageCopyContext pageCopyContext = pageCopyEvent.getPageCopyContext();
        final HstRequestContext requestContext = pageCopyContext.getRequestContext();
        final Mount sourceMount = pageCopyContext.getEditingMount();
        final Mount targetMount = pageCopyContext.getTargetMount();
        final String sourceContentBasePath = sourceMount.getContentPath();
        final String targetContentBasePath = targetMount.getContentPath();

        try {
            final Node sourceContentBaseNode = requestContext.getSession().getNode(sourceContentBasePath);
            final Node targetContentBaseNode = requestContext.getSession().getNode(targetContentBasePath);
            String sourceTranslationLanguage = HippoFolderDocumentUtils
                    .getHippoTranslationLanguage(sourceContentBaseNode);
            String targetTranslationLanguage = HippoFolderDocumentUtils
                    .getHippoTranslationLanguage(targetContentBaseNode);

            if (StringUtils.isBlank(sourceTranslationLanguage)) {
                throw new IllegalStateException(
                        "Blank translation language in the source base content at '" + sourceContentBasePath + "'.");
            }

            if (StringUtils.isBlank(targetTranslationLanguage)) {
                throw new IllegalStateException(
                        "Blank translation language in the target base content at '" + targetContentBasePath + "'.");
            }

            if (StringUtils.equals(sourceTranslationLanguage, targetTranslationLanguage)) {
                throw new IllegalStateException(
                        "The same translation language of the source and the target base content. Source='"
                                + sourceContentBasePath + "'. Target='" + targetContentBasePath + "'.");
            }

            if (isCopyDocumentsLinkedBySourcePage()) {
                final Set<String> documentPathSet = getDocumentPathSetInPage(pageCopyContext.getSourcePage());

                if (!documentPathSet.isEmpty()) {
                    if (!StringUtils.equals(sourceMount.getContentPath(), targetMount.getContentPath())) {
                        copyDocuments(pageCopyContext.getRequestContext().getSession(), documentPathSet,
                                sourceContentBaseNode, targetContentBaseNode);
                    } else {
                        log.info(
                                "Linked document copying step skipped because the content path of the target mount is the same as that of the source mount.");
                    }
                } else {
                    log.info("No linked document founds in the source page.");
                }
            } else {
                log.info(
                        "Linked document copying step skipped because 'copyDocumentsLinkedBySourcePage' is turned off.");
            }
        } catch (RuntimeException e) {
            pageCopyEvent.setException(e);
        } catch (Exception e) {
            pageCopyEvent.setException(
                    new RuntimeException("Failed to handle page copy event properly. " + e.toString(), e));
        }
    }
    */
    private void copyDocuments(final Session session, final Set<String> documentPathSet,
            final Node sourceContentBaseNode, final Node targetContentBaseNode) {
        try {
            final String sourceContentBasePath = sourceContentBaseNode.getPath();
            final String targetContentBasePath = targetContentBaseNode.getPath();
            final String targetTranslationLanguage = HippoFolderDocumentUtils
                    .getHippoTranslationLanguage(targetContentBaseNode);

            Node sourceDocumentHandleNode;
            String targetDocumentAbsPath;
            String targetFolderAbsPath;

            for (String documentPath : documentPathSet) {
                if (StringUtils.startsWith(documentPath, "/")) {
                    log.info(
                            "Skiping '{}' because it's an absolute jcr path, not relative to source mount content base",
                            documentPath);
                    continue;
                }

                if (!sourceContentBaseNode.hasNode(documentPath)) {
                    log.info("Skiping '{}' because it doesn't exist under '{}'.", documentPath, sourceContentBasePath);
                    continue;
                }

                sourceDocumentHandleNode = HippoFolderDocumentUtils
                        .getHippoDocumentHandle(sourceContentBaseNode.getNode(documentPath));

                if (sourceDocumentHandleNode == null) {
                    log.info("Skiping '{}' because there's no document at the location under '{}'.", documentPath,
                            sourceContentBasePath);
                    continue;
                }

                targetDocumentAbsPath = targetContentBasePath + "/" + documentPath;

                if (HippoFolderDocumentUtils.documentExists(session, targetDocumentAbsPath)) {
                    log.info("Skiping '{}' because it already exists under '{}'.", documentPath, targetContentBasePath);
                    continue;
                }

                targetFolderAbsPath = StringUtils.substringBeforeLast(targetDocumentAbsPath, "/");

                if (!HippoFolderDocumentUtils.folderExists(session, targetFolderAbsPath)) {
                    String sourceFolderRelPath = sourceDocumentHandleNode.getParent().getPath()
                            .substring(sourceContentBasePath.length() + 1);

                    translateFolders(session, sourceContentBaseNode, sourceFolderRelPath, targetContentBaseNode,
                            targetTranslationLanguage);
                }

                getDocumentManagementServiceClient().translateDocument(sourceDocumentHandleNode.getPath(),
                        targetTranslationLanguage, sourceDocumentHandleNode.getName());
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
