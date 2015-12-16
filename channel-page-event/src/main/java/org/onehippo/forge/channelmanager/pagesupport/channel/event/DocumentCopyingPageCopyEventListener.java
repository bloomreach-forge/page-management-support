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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

import org.apache.commons.lang.StringUtils;
import org.hippoecm.hst.configuration.components.HstComponentConfiguration;
import org.hippoecm.hst.configuration.hosting.Mount;
import org.hippoecm.hst.configuration.site.HstSite;
import org.hippoecm.hst.core.container.ComponentManager;
import org.hippoecm.hst.core.container.ComponentManagerAware;
import org.hippoecm.hst.core.jcr.RuntimeRepositoryException;
import org.hippoecm.hst.core.linking.DocumentParamsScanner;
import org.hippoecm.hst.core.request.HstRequestContext;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyContext;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyEvent;
import org.hippoecm.hst.pagecomposer.jaxrs.services.exceptions.ClientError;
import org.hippoecm.hst.pagecomposer.jaxrs.services.exceptions.ClientException;
import org.hippoecm.repository.translation.HippoTranslationNodeType;
import org.hippoecm.repository.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hippoecm.hst.configuration.HstNodeTypes.COMPONENT_PROPERTY_REFERECENCECOMPONENT;

/**
 * <code>org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyEvent</code> event handler which is to be registered through
 * {@link ComponentManager#registerEventSubscriber(Object)} and unregistered through
 * {@link ComponentManager#unregisterEventSubscriber(Object)} during the HST-2 based web application lifecycle.
 * <P>
 * Basically this event handler scans all the linked documents in a page and its components
 * and copy each document from the source channel to the target channel if not existing in the target channel.
 * </P>
 */
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

        final String sourceContentBasePath = sourceMount.getContentPath().intern();
        final String targetContentBasePath = targetMount.getContentPath().intern();

        // synchronize interned targetContentBasePath to disallow concurrent document copying on the same target channel
        synchronized (targetContentBasePath) {
            try {
                final Node sourceContentBaseNode = requestContext.getSession().getNode(sourceContentBasePath);
                final Node targetContentBaseNode = requestContext.getSession().getNode(targetContentBasePath);
                String sourceTranslationLanguage = HippoFolderDocumentUtils
                        .getHippoTranslationLanguage(sourceContentBaseNode);
                String targetTranslationLanguage = HippoFolderDocumentUtils
                        .getHippoTranslationLanguage(targetContentBaseNode);

                if (StringUtils.isBlank(sourceTranslationLanguage)) {
                    throw new IllegalStateException("Blank translation language in the source base content at '"
                            + sourceContentBasePath + "'.");
                }

                if (StringUtils.isBlank(targetTranslationLanguage)) {
                    throw new IllegalStateException("Blank translation language in the target base content at '"
                            + targetContentBasePath + "'.");
                }

                if (isCopyDocumentsLinkedBySourcePage()) {
                    if (StringUtils.equals(sourceContentBasePath, targetContentBasePath)) {
                        log.info(
                                "No need to copy documents because the source and target channel have the same content base path: {}'",
                                sourceContentBasePath);
                    } else {
                        if (StringUtils.equals(sourceTranslationLanguage, targetTranslationLanguage)) {
                            throw new IllegalStateException(
                                    "The same translation language of the source and the target base content. Source='"
                                            + sourceContentBasePath + "'. Target='" + targetContentBasePath + "'.");
                        }

                        final Set<String> documentPathSet = getDocumentPathSetInPage(pageCopyContext);

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
                    }
                } else {
                    log.info(
                            "Linked document copying step skipped because 'copyDocumentsLinkedBySourcePage' is turned off.");
                }
            } catch (ClientException e) {
                log.error("Failed to handle page copy event properly.", e);
                pageCopyEvent.setException(e);
            } catch (Exception e) {
                log.error("Failed to handle page copy event properly.", e);
                final String clientMessage = "Failed to handle page copy event properly. " + e.toString();
                pageCopyEvent.setException(new ClientException(clientMessage, ClientError.ITEM_CANNOT_BE_CLONED,
                        Collections.singletonMap("errorReason", clientMessage)));
            }
        }
    }

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
                            "Skipping '{}' because it's an absolute jcr path, not relative to source mount content base",
                            documentPath);
                    continue;
                }

                if (!sourceContentBaseNode.hasNode(documentPath)) {
                    log.info("Skipping '{}' because it doesn't exist under '{}'.", documentPath, sourceContentBasePath);
                    continue;
                }

                sourceDocumentHandleNode = HippoFolderDocumentUtils
                        .getHippoDocumentHandle(sourceContentBaseNode.getNode(documentPath));

                if (sourceDocumentHandleNode == null) {
                    log.info("Skipping '{}' because there's no document at the location under '{}'.", documentPath,
                            sourceContentBasePath);
                    continue;
                }

                targetDocumentAbsPath = targetContentBasePath + "/" + documentPath;

                if (HippoFolderDocumentUtils.documentExists(session, targetDocumentAbsPath)) {
                    log.info("Skipping '{}' because it already exists under '{}'.", documentPath, targetContentBasePath);
                    continue;
                }

                targetFolderAbsPath = StringUtils.substringBeforeLast(targetDocumentAbsPath, "/");

                if (HippoFolderDocumentUtils.folderExists(session, targetFolderAbsPath)) {
                    Node targetFolderNode = session.getNode(targetFolderAbsPath);

                    if (!targetFolderNode.isNodeType(HippoTranslationNodeType.NT_TRANSLATED)) {
                        final String clientMessage = "Cannot copy documents because the target folder at '"
                                + targetFolderAbsPath + "' is not type of " + HippoTranslationNodeType.NT_TRANSLATED
                                + ".";
                        throw new ClientException(clientMessage, ClientError.INVALID_NODE_TYPE,
                                Collections.singletonMap("errorReason", clientMessage));
                    } else {
                        Node sourceFolderNode = sourceDocumentHandleNode.getParent();
                        String sourceFolderTranslationId = JcrUtils.getStringProperty(sourceFolderNode,
                                HippoTranslationNodeType.ID, null);
                        String targetFolderTranslationId = JcrUtils.getStringProperty(targetFolderNode,
                                HippoTranslationNodeType.ID, null);
                        if (!StringUtils.equals(sourceFolderTranslationId, targetFolderTranslationId)) {
                            final String clientMessage = "Cannot copy documents because the translation ID of target folder at '"
                                    + targetFolderAbsPath + "' doesn't match with that of source folder at '"
                                    + sourceFolderNode.getPath() + "'. '" + targetFolderTranslationId
                                    + "' (target) vs. '" + sourceFolderTranslationId + "' (source).";
                            throw new ClientException(clientMessage, ClientError.ITEM_CANNOT_BE_CLONED,
                                    Collections.singletonMap("errorReason", clientMessage));
                        }
                    }
                } else {
                    String sourceFolderRelPath = sourceDocumentHandleNode.getParent().getPath()
                            .substring(sourceContentBasePath.length() + 1);

                    translateFolders(session, sourceContentBaseNode, sourceFolderRelPath, targetContentBaseNode,
                            targetTranslationLanguage);
                }

                getDocumentManagementServiceClient().translateDocument(sourceDocumentHandleNode.getPath(),
                        targetTranslationLanguage, sourceDocumentHandleNode.getName());
            }
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            final String clientMessage = "Failed to copy all the linked documents. " + e.toString();
            throw new ClientException(clientMessage, ClientError.ITEM_CANNOT_BE_CLONED,
                    Collections.singletonMap("errorReason", clientMessage));
        }
    }

    /**
     * In case of targeting, you also get all the locations for the
     * variants in the list. The returned set can have values that start
     * with a '/' or without. If they start with a '/', they are absolute
     * paths (from jcr root). If they don't start with a '/', they are
     * relative to the channel content root.
     * @param pageCopyContext
     * @return
     */
    private Set<String> getDocumentPathSetInPage(final PageCopyContext pageCopyContext) throws RepositoryException {
        final FilterPresentComponentConfigurations filterPresentComponentConfigurations
                = new FilterPresentComponentConfigurations(pageCopyContext.getSourcePage(), pageCopyContext.getEditingMount().getHstSite(),
                pageCopyContext.getTargetMount().getHstSite(),
                pageCopyContext.getRequestContext().getSession());

        List<String> documentPathList = DocumentParamsScanner.findDocumentPathsRecursive(pageCopyContext.getSourcePage(),
                Thread.currentThread().getContextClassLoader(), filterPresentComponentConfigurations);

        return new LinkedHashSet<String>(documentPathList);
    }

    public static class FilterPresentComponentConfigurations implements Predicate<HstComponentConfiguration> {

        private final Set<String> filteredConfigurationUUIDs;
        public FilterPresentComponentConfigurations(final HstComponentConfiguration sourceConfig, final HstSite sourceSite, final HstSite targetSite, final Session session) {
            filteredConfigurationUUIDs =  new HashSet<>();
            populateSkipSet(sourceConfig, sourceSite, targetSite, session, filteredConfigurationUUIDs);
        }

        private void populateSkipSet(final HstComponentConfiguration sourceConfig,
                                     final HstSite sourceSite,
                                     final HstSite targetSite,
                                     final Session session,
                                     final Set<String> skipSet) {
            try {
                final Node sourceNode = session.getNodeByIdentifier(sourceConfig.getCanonicalIdentifier());
                if (sourceNode.hasProperty(COMPONENT_PROPERTY_REFERECENCECOMPONENT)) {
                    String reference = sourceNode.getProperty(COMPONENT_PROPERTY_REFERECENCECOMPONENT).getString();
                    if (!StringUtils.isBlank(reference)) {
                        final HstComponentConfiguration targetReference = targetSite.getComponentsConfiguration().getComponentConfiguration(reference);
                        final HstComponentConfiguration sourceReference = sourceSite.getComponentsConfiguration().getComponentConfiguration(reference);
                        if (targetReference != null) {
                            log.debug("Skipping '{}' and descendants because targetSite '{}' already has a resolvable reference for '{}'",
                                    sourceConfig, targetSite, reference);
                            if (sourceReference != null) {
                                // sourceReference is never expected to be null, but just in case a null check
                                populateSelfAndDescending(sourceReference, skipSet);
                            }
                            // no need to check descendant configs
                            return;
                        }
                    }
                }
            } catch (RepositoryException e) {
                throw new RuntimeRepositoryException(e);
            }
            for (HstComponentConfiguration child : sourceConfig.getChildren().values()) {
                populateSkipSet(child, sourceSite, targetSite, session, skipSet);
            }
        }

        private void populateSelfAndDescending(final HstComponentConfiguration current,
                                               final Set<String> skipSet) {
            skipSet.add(current.getCanonicalIdentifier());
            for (HstComponentConfiguration child : current.getChildren().values()) {
                populateSelfAndDescending(child, skipSet);
            }
        }

        @Override
        public boolean test(final HstComponentConfiguration sourceConfig) {
            if (filteredConfigurationUUIDs.contains(sourceConfig.getCanonicalIdentifier())) {
                return false;
            }
            return true;
        }
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
