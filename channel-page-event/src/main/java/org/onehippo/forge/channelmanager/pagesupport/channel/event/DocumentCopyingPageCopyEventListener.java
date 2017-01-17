/*
 * Copyright 2015-2017 Hippo B.V. (http://www.onehippo.com)
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

import static org.hippoecm.hst.configuration.HstNodeTypes.COMPONENT_PROPERTY_REFERECENCECOMPONENT;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.Predicate;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

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
import org.hippoecm.repository.HippoStdNodeType;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.translation.HippoTranslationNodeType;
import org.hippoecm.repository.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

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

    private static final String TRANSLATED_FOLDER_QUERY = "/jcr:root{0}//element(*,hippostd:folder)[@hippotranslation:id=''{1}'']";

    private static final String TRANSLATED_DOCUMENT_HANDLE_QUERY = "/jcr:root{0}//element(*,hippostdpubwf:document)[@hippotranslation:id=''{1}'']/..";

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

    /**
     * Custom event handler before {@link #onPageCopyEvent(PageCopyEvent)} is invoked.
     * An extended class from this can implement this method if it needs to process some custom tasks before the
     * normal page copy event handling.
     * @param pageCopyEvent page copy event
     */
    protected void onBeforePageCopyEvent(PageCopyEvent pageCopyEvent) {
    }

    /**
     * Custom event handler after {@link #onPageCopyEvent(PageCopyEvent)} is invoked.
     * An extended class from this can implement this method if it needs to process some custom tasks after the
     * normal page copy event handling.
     * @param pageCopyEvent page copy event
     */
    protected void onAfterPageCopyEvent(PageCopyEvent pageCopyEvent) {
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
                onBeforePageCopyEvent(pageCopyEvent);

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

                onAfterPageCopyEvent(pageCopyEvent);
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

    private void copyDocuments(final Session session, final Set<String> sourceDocumentPathSet,
            final Node sourceContentBaseNode, final Node targetContentBaseNode) {
        try {
            final String sourceContentBasePath = sourceContentBaseNode.getPath();
            final String targetContentBasePath = targetContentBaseNode.getPath();
            final String targetTranslationLanguage = HippoFolderDocumentUtils
                    .getHippoTranslationLanguage(targetContentBaseNode);

            Node sourceDocumentHandleNode;
            Node targetDocumentHandleNode;
            String targetDocumentAbsPath;
            String targetFolderAbsPath;
            String targetFolderRelPath;

            for (String sourceDocumentPath : sourceDocumentPathSet) {
                if (StringUtils.startsWith(sourceDocumentPath, "/")) {
                    log.info(
                            "Skipping '{}' because it's an absolute jcr path, not relative to source mount content base",
                            sourceDocumentPath);
                    continue;
                }

                if (!sourceContentBaseNode.hasNode(sourceDocumentPath)) {
                    log.info("Skipping '{}' because it doesn't exist under '{}'.", sourceDocumentPath, sourceContentBasePath);
                    continue;
                }

                sourceDocumentHandleNode = HippoFolderDocumentUtils
                        .getHippoDocumentHandle(sourceContentBaseNode.getNode(sourceDocumentPath));

                if (sourceDocumentHandleNode == null) {
                    log.info("Skipping '{}' because there's no document at the location under '{}'.", sourceDocumentPath,
                            sourceContentBasePath);
                    continue;
                }

                targetDocumentHandleNode = findTargetTranslatedDocumentHandleNode(targetContentBaseNode, sourceDocumentHandleNode);

                if (targetDocumentHandleNode != null) {
                    log.info("Skipping '{}' because there exists a translated document at '{}'.", sourceDocumentPath,
                            targetDocumentHandleNode.getPath());
                    continue;
                }

                targetDocumentAbsPath = resolveTargetDocumentAbsPath(sourceContentBaseNode, targetContentBaseNode, sourceDocumentPath);

                if (HippoFolderDocumentUtils.documentExists(session, targetDocumentAbsPath)) {
                    log.info("Skipping '{}' because it already exists under '{}'.", sourceDocumentPath, targetContentBasePath);
                    continue;
                }

                targetFolderAbsPath = StringUtils.substringBeforeLast(targetDocumentAbsPath, "/");
                targetFolderRelPath = StringUtils.substringAfter(targetFolderAbsPath, targetContentBasePath + "/");

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
                            targetFolderRelPath, targetTranslationLanguage);
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
     * Resolves target document absolute path under {@code targetContentBaseNode},
     * corresponding to the {@code sourceDocumentPath} under {@code sourceContentBaseNode}.
     * @param sourceContentBaseNode source content base folder node
     * @param targetContentBaseNode target content base folder node
     * @param sourceDocumentPath source document relative path
     * @return corresponding target document absolute path
     * @throws RepositoryException if any repository exception occurs
     */
    private String resolveTargetDocumentAbsPath(final Node sourceContentBaseNode, final Node targetContentBaseNode,
            final String sourceDocumentPath) throws RepositoryException {
        Node sourceDocumentHandleNode = sourceContentBaseNode.getNode(sourceDocumentPath);
        Node sourceFolderNode = sourceDocumentHandleNode.getParent();
        Node targetFolderNode = findTargetTranslatedFolderNode(targetContentBaseNode, sourceFolderNode);

        if (targetFolderNode != null) {
            return targetFolderNode.getPath() + "/" + sourceDocumentHandleNode.getName();
        }

        Stack<String> targetFolderNameStack = new Stack<>();
        targetFolderNameStack.push(sourceFolderNode.getName());

        sourceFolderNode = sourceFolderNode.getParent();

        while (!sourceFolderNode.isSame(sourceContentBaseNode)) {
            targetFolderNode = findTargetTranslatedFolderNode(targetContentBaseNode, sourceFolderNode);

            if (targetFolderNode != null) {
                String folderPath = StringUtils.removeStart(targetFolderNode.getPath(),
                        targetContentBaseNode.getPath() + "/");
                targetFolderNameStack.push(folderPath);
                break;
            } else {
                targetFolderNameStack.push(sourceFolderNode.getName());
            }

            sourceFolderNode = sourceFolderNode.getParent();
        }

        return targetContentBaseNode.getPath() + "/" + StringUtils.join(popAllToList(targetFolderNameStack), "/") + "/"
                + sourceDocumentHandleNode.getName();
    }

    /**
     * Find translated folder node under {@code targetContentBaseNode} for the {@code sourceFolderNode}.
     * @param targetContentBaseNode target content base folder node
     * @param sourceFolderNode source folder node
     * @return translated folder node under {@code targetContentBaseNode} for the {@code sourceFolderNode}
     * @throws RepositoryException if repository exception occurs
     */
    private Node findTargetTranslatedFolderNode(final Node targetContentBaseNode, final Node sourceFolderNode)
            throws RepositoryException {
        if (!sourceFolderNode.isNodeType(HippoStdNodeType.NT_FOLDER)
                || !sourceFolderNode.isNodeType(HippoTranslationNodeType.NT_TRANSLATED)) {
            return null;
        }

        Node translatedFolderNode = null;

        final String translationId = JcrUtils.getStringProperty(sourceFolderNode, HippoTranslationNodeType.ID, null);
        final String statement = MessageFormat.format(TRANSLATED_FOLDER_QUERY, targetContentBaseNode.getPath(),
                translationId);
        final Query query = targetContentBaseNode.getSession().getWorkspace().getQueryManager().createQuery(statement,
                Query.XPATH);

        final List<Node> translatedFolderNodes = new ArrayList<>();
        final QueryResult result = query.execute();
        Node node;

        for (NodeIterator nodeIt = result.getNodes(); nodeIt.hasNext();) {
            node = nodeIt.nextNode();
            if (node != null) {
                translatedFolderNodes.add(node);
            }
        }

        if (!translatedFolderNodes.isEmpty()) {
            translatedFolderNode = translatedFolderNodes.get(0);

            if (translatedFolderNodes.size() > 1) {
                List<String> translatedFolderNodePaths = new ArrayList<>();
                for (Node folderNode : translatedFolderNodes) {
                    translatedFolderNodePaths.add(folderNode.getPath());
                }
                log.warn("Multiple translated folder nodes found for translation ID, '{}': {}", translationId,
                        translatedFolderNodePaths);
            }
        }

        return translatedFolderNode;
    }

    /**
     * Find translated document handle node under {@code targetContentBaseNode} for the {@code sourceDocumentHandleNode}.
     * @param targetContentBaseNode target content base folder node
     * @param sourceDocumentHandleNode source document handle node
     * @return translated document handle node under {@code targetContentBaseNode} for the {@code sourceDocumentHandleNode}
     * @throws RepositoryException if repository exception occurs
     */
    private Node findTargetTranslatedDocumentHandleNode(final Node targetContentBaseNode, final Node sourceDocumentHandleNode)
            throws RepositoryException {
        if (!sourceDocumentHandleNode.isNodeType(HippoNodeType.NT_HANDLE)
                || !sourceDocumentHandleNode.hasNode(sourceDocumentHandleNode.getName())) {
            return null;
        }

        final Node sourceDocumentVariantNode = sourceDocumentHandleNode.getNode(sourceDocumentHandleNode.getName());

        if (!sourceDocumentVariantNode.isNodeType(HippoTranslationNodeType.NT_TRANSLATED)) {
            return null;
        }

        final String translationId = JcrUtils.getStringProperty(sourceDocumentVariantNode, HippoTranslationNodeType.ID, null);

        final String statement = MessageFormat.format(TRANSLATED_DOCUMENT_HANDLE_QUERY, targetContentBaseNode.getPath(),
                translationId);
        final Query query = targetContentBaseNode.getSession().getWorkspace().getQueryManager().createQuery(statement,
                Query.XPATH);

        final List<Node> translatedDocumentHandleNodes = new ArrayList<>();
        final QueryResult result = query.execute();
        Node node;

        for (NodeIterator nodeIt = result.getNodes(); nodeIt.hasNext();) {
            node = nodeIt.nextNode();
            if (node != null) {
                translatedDocumentHandleNodes.add(node);
            }
        }

        Node translatedDocumentHandleNode = null;

        if (!translatedDocumentHandleNodes.isEmpty()) {
            translatedDocumentHandleNode = translatedDocumentHandleNodes.get(0);

            if (translatedDocumentHandleNodes.size() > 1) {
                List<String> translatedDocumentHandleNodePaths = new ArrayList<>();
                for (Node handleNode : translatedDocumentHandleNodes) {
                    translatedDocumentHandleNodePaths.add(handleNode.getPath());
                }
                log.warn("Multiple translated document handle nodes found for translation ID, '{}': {}", translationId,
                        translatedDocumentHandleNodePaths);
            }
        }

        return translatedDocumentHandleNode;
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
                            // no need to check descendant configurations
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
            final String sourceFolderRelPath, final Node targetBaseFolderNode, final String targetFolderRelPath,
            final String targetTranslationLanguage) throws Exception {
        String[] sourceFolderNodeNames = StringUtils.split(sourceFolderRelPath, "/");
        String[] targetFolderNodeNames = StringUtils.split(targetFolderRelPath, "/");
        String sourceFolderLocation = sourceBaseFolderNode.getPath();
        String targetFolderLocation = targetBaseFolderNode.getPath();

        String sourceFolderNodeName;
        String targetFolderNodeName;

        for (int i = 0; i < sourceFolderNodeNames.length; i++) {
            sourceFolderNodeName = sourceFolderNodeNames[i];
            targetFolderNodeName = (targetFolderNodeNames.length > i) ? targetFolderNodeNames[i] : sourceFolderNodeName;

            sourceFolderLocation += "/" + sourceFolderNodeName;

            if (!HippoFolderDocumentUtils.folderExists(session, sourceFolderLocation)) {
                throw new IllegalArgumentException("Source folder doesn't exist at '" + sourceFolderLocation + "'.");
            }

            targetFolderLocation += "/" + targetFolderNodeName;

            if (!HippoFolderDocumentUtils.folderExists(session, targetFolderLocation)) {
                getDocumentManagementServiceClient().translateFolder(sourceFolderLocation, targetTranslationLanguage,
                        targetFolderNodeName);
            }
        }
    }

    private static <T> List<T> popAllToList(final Stack<T> stack) {
        if (stack == null) {
            return Collections.emptyList();
        }

        List<T> list = new LinkedList<>();
        T object;

        while (!stack.empty()) {
            object = stack.pop();
            list.add(object);
        }

        return list;
    }
}
