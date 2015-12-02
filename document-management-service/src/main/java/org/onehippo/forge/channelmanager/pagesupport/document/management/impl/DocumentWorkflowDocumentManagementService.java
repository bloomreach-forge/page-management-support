/*
 * Copyright 2015-2015 Hippo B.V. (http://www.onehippo.com)
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

import java.rmi.RemoteException;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.hippoecm.repository.HippoStdNodeType;
import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.translation.TranslationWorkflow;
import org.onehippo.forge.channelmanager.pagesupport.document.management.DocumentManagementService;
import org.onehippo.repository.documentworkflow.DocumentWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentWorkflowDocumentManagementService implements DocumentManagementService {

    private static Logger log = LoggerFactory.getLogger(DocumentWorkflowDocumentManagementService.class);

    private Session session;

    /**
     * The workflow category name to get a document workflow. 
     */
    private String documentWorkflowCategory = "default";

    @Override
    public void initialize(Session session) {
        this.session = session;
    }

    @Override
    public void destroy() {
        session = null;
    }

    @Override
    public boolean obtainEditableDocument(String documentLocation) {
        log.debug("##### obtainEditableDocument('{}')", documentLocation);

        if (StringUtils.isBlank(documentLocation)) {
            throw new IllegalArgumentException("Invalid document location: '" + documentLocation + "'.");
        }

        boolean obtained = false;

        try {
            if (!getSession().nodeExists(documentLocation)) {
                throw new IllegalArgumentException("Document doesn't exist at '" + documentLocation + "'.");
            }

            Node documentHandleNode = HippoWorkflowUtils.getHippoDocumentHandle(getSession().getNode(documentLocation));

            if (documentHandleNode == null) {
                throw new IllegalArgumentException("Document handle is not found at '" + documentLocation + "'.");
            }

            DocumentWorkflow documentWorkflow = getDocumentWorkflow(documentHandleNode);

            Boolean obtainEditableInstance = (Boolean) documentWorkflow.hints().get("obtainEditableInstance");

            if (BooleanUtils.isTrue(obtainEditableInstance)) {
                documentWorkflow.obtainEditableInstance();
                obtained = true;
            } else {
                throw new IllegalStateException(
                        "Document at '" + documentLocation + "' is not allowed to obtain an editable instance.");
            }
        } catch (Exception e) {
            log.error("Failed to obtain editable instance on document.", e);
            throw new RuntimeException(
                    "Failed to obtain editable instance on document at '" + documentLocation + "'. " + e);
        }

        return obtained;
    }

    @Override
    public boolean disposeEditableDocument(String documentLocation) {
        log.debug("##### disposeEditableDocument('{}')", documentLocation);

        if (StringUtils.isBlank(documentLocation)) {
            throw new IllegalArgumentException("Invalid document location: '" + documentLocation + "'.");
        }

        boolean disposed = false;

        try {
            if (!getSession().nodeExists(documentLocation)) {
                throw new IllegalArgumentException("Document doesn't exist at '" + documentLocation + "'.");
            }

            Node documentHandleNode = HippoWorkflowUtils.getHippoDocumentHandle(getSession().getNode(documentLocation));

            if (documentHandleNode == null) {
                throw new IllegalArgumentException("Document handle is not found at '" + documentLocation + "'.");
            }

            DocumentWorkflow documentWorkflow = getDocumentWorkflow(documentHandleNode);

            Boolean disposeEditableInstance = (Boolean) documentWorkflow.hints().get("disposeEditableInstance");

            if (BooleanUtils.isTrue(disposeEditableInstance)) {
                documentWorkflow.disposeEditableInstance();
                disposed = true;
            } else {
                throw new IllegalStateException(
                        "Document at '" + documentLocation + "' is not allowed to dispose an editable instance.");
            }
        } catch (Exception e) {
            log.error("Failed to dispose editable instance on document.", e);
            throw new RuntimeException(
                    "Failed to dispose editable instance on document at '" + documentLocation + "'. " + e);
        }

        return disposed;
    }

    @Override
    public boolean commitEditableDocument(String documentLocation) {
        log.debug("##### commitEditableDocument('{}')", documentLocation);

        if (StringUtils.isBlank(documentLocation)) {
            throw new IllegalArgumentException("Invalid document location: '" + documentLocation + "'.");
        }

        boolean committed = false;

        try {
            if (!getSession().nodeExists(documentLocation)) {
                throw new IllegalArgumentException("Document doesn't exist at '" + documentLocation + "'.");
            }

            Node documentHandleNode = HippoWorkflowUtils.getHippoDocumentHandle(getSession().getNode(documentLocation));

            if (documentHandleNode == null) {
                throw new IllegalArgumentException("Document handle is not found at '" + documentLocation + "'.");
            }

            DocumentWorkflow documentWorkflow = getDocumentWorkflow(documentHandleNode);

            Boolean commitEditableInstance = (Boolean) documentWorkflow.hints().get("commitEditableInstance");

            if (BooleanUtils.isTrue(commitEditableInstance)) {
                documentWorkflow.commitEditableInstance();
                committed = true;
            } else {
                throw new IllegalStateException(
                        "Document at '" + documentLocation + "' is not allowed to commit an editable instance.");
            }
        } catch (Exception e) {
            log.error("Failed to commit editable instance on document.", e);
            throw new RuntimeException(
                    "Failed to commit editable instance on document at '" + documentLocation + "'. " + e);
        }

        return committed;
    }

    @Override
    public boolean depublishDocument(String documentLocation) {
        log.debug("##### depublishDocument('{}')", documentLocation);

        if (StringUtils.isBlank(documentLocation)) {
            throw new IllegalArgumentException("Invalid document location: '" + documentLocation + "'.");
        }

        boolean depublished = false;

        try {
            if (!getSession().nodeExists(documentLocation)) {
                throw new IllegalArgumentException("Document doesn't exist at '" + documentLocation + "'.");
            }

            Node documentHandleNode = HippoWorkflowUtils.getHippoDocumentHandle(getSession().getNode(documentLocation));

            if (documentHandleNode == null) {
                throw new IllegalArgumentException("Document handle is not found at '" + documentLocation + "'.");
            }

            DocumentWorkflow documentWorkflow = getDocumentWorkflow(documentHandleNode);

            Boolean isLive = (Boolean) documentWorkflow.hints().get("isLive");

            if (BooleanUtils.isFalse(isLive)) {
                // already offline, so just return true
                depublished = true;
            } else {
                Boolean depublish = (Boolean) documentWorkflow.hints().get("depublish");

                if (!BooleanUtils.isTrue(depublish)) {
                    throw new IllegalStateException(
                            "Document at '" + documentLocation + "' doesn't have depublish action.");
                }

                documentWorkflow.depublish();
                depublished = true;
            }
        } catch (RepositoryException | WorkflowException | RemoteException e) {
            log.error("Failed to depublish document at '{}'.", documentLocation, e);
            throw new RuntimeException("Failed to depublish document at '" + documentLocation + "'. " + e);
        }

        return depublished;
    }

    @Override
    public boolean publishDocument(String documentLocation) {
        log.debug("##### publishDocument('{}')", documentLocation);

        if (StringUtils.isBlank(documentLocation)) {
            throw new IllegalArgumentException("Invalid document location: '" + documentLocation + "'.");
        }

        boolean published = false;

        try {
            if (!getSession().nodeExists(documentLocation)) {
                throw new IllegalArgumentException("Document doesn't exist at '" + documentLocation + "'.");
            }

            Node documentHandleNode = HippoWorkflowUtils.getHippoDocumentHandle(getSession().getNode(documentLocation));

            if (documentHandleNode == null) {
                throw new IllegalArgumentException("Document handle is not found at '" + documentLocation + "'.");
            }

            DocumentWorkflow documentWorkflow = getDocumentWorkflow(documentHandleNode);

            Boolean publish = (Boolean) documentWorkflow.hints().get("publish");

            if (!BooleanUtils.isTrue(publish)) {
                throw new IllegalStateException("Document at '" + documentLocation + "' doesn't have publish action.");
            }

            documentWorkflow.publish();
            published = true;
        } catch (RepositoryException | WorkflowException | RemoteException e) {
            log.error("Failed to publish document at '{}'.", documentLocation, e);
            throw new RuntimeException("Failed to publish document at '" + documentLocation + "'. " + e);
        }

        return published;
    }

    @Override
    public String copyDocument(String sourceDocumentLocation, String targetFolderLocation,
            String targetDocumentNodeName) {
        log.debug("##### copyDocument('{}', '{}', '{}')", sourceDocumentLocation, targetFolderLocation,
                targetDocumentNodeName);

        String targetDocumentLocation = null;

        try {
            if (!getSession().nodeExists(sourceDocumentLocation)) {
                throw new IllegalArgumentException(
                        "Source document doesn't exist at '" + sourceDocumentLocation + "'.");
            }

            final Node targetFolderNode = HippoWorkflowUtils.createMissingHippoFolders(getSession(),
                    targetFolderLocation);

            if (targetFolderNode == null) {
                throw new IllegalArgumentException("Target folder doesn't exist at '" + targetFolderLocation + "'.");
            }

            Node sourceDocumentHandleNode = HippoWorkflowUtils
                    .getHippoDocumentHandle(getSession().getNode(sourceDocumentLocation));

            if (sourceDocumentHandleNode == null) {
                throw new IllegalArgumentException(
                        "Source document handle is not found at '" + sourceDocumentLocation + "'.");
            }

            DocumentWorkflow documentWorkflow = getDocumentWorkflow(sourceDocumentHandleNode);
            Boolean copy = (Boolean) documentWorkflow.hints().get("copy");

            if (BooleanUtils.isTrue(copy)) {
                documentWorkflow.copy(new Document(targetFolderNode), targetDocumentNodeName);
                targetDocumentLocation = targetFolderNode.getNode(targetDocumentNodeName).getPath();
            } else {
                throw new IllegalStateException("Copy action not available on document at '" + sourceDocumentLocation
                        + "' to '" + targetFolderLocation + "/" + targetDocumentNodeName + "'.");
            }
        } catch (RepositoryException | WorkflowException | RemoteException e) {
            log.error("Failed to copy document at '{}' to '{}/{}'.", sourceDocumentLocation, targetFolderLocation,
                    targetDocumentNodeName, e);
            throw new RuntimeException("Failed to copy document at '" + sourceDocumentLocation + "' to '"
                    + targetFolderLocation + "/" + targetDocumentNodeName + "'. " + e);
        }

        return targetDocumentLocation;
    }

    @Override
    public String translateDocument(String sourceDocumentLocation, String targetLanguage, String targetDocumentNodeName) {
        log.debug("##### translateDocument('{}', '{}', '{}')", sourceDocumentLocation, targetLanguage,
                targetDocumentNodeName);

        String targetDocumentLocation = null;

        try {
            if (!getSession().nodeExists(sourceDocumentLocation)) {
                throw new IllegalArgumentException(
                        "Source document doesn't exist at '" + sourceDocumentLocation + "'.");
            }

            Node sourceDocumentHandleNode = HippoWorkflowUtils
                    .getHippoDocumentHandle(getSession().getNode(sourceDocumentLocation));

            if (sourceDocumentHandleNode == null) {
                throw new IllegalArgumentException(
                        "Source document handle is not found at '" + sourceDocumentLocation + "'.");
            }

            Node translationVariantNode = null;
            Map<String, Node> documentVariantsMap = HippoWorkflowUtils.getDocumentVariantsMap(sourceDocumentHandleNode);

            if (documentVariantsMap.containsKey(HippoStdNodeType.UNPUBLISHED)) {
                translationVariantNode = documentVariantsMap.get(HippoStdNodeType.UNPUBLISHED);
            } else if (documentVariantsMap.containsKey(HippoStdNodeType.PUBLISHED)) {
                translationVariantNode = documentVariantsMap.get(HippoStdNodeType.PUBLISHED);
            }

            if (translationVariantNode == null) {
                throw new IllegalStateException("No available unpublished or published variant in document at '"
                        + sourceDocumentLocation + "'.");
            }

            TranslationWorkflow documentTranslationWorkflow = getDocumentTranslationWorkflow(translationVariantNode);
            Document translatedDocument = documentTranslationWorkflow.addTranslation(targetLanguage, targetDocumentNodeName);
            Node translatedDocumentHandleNode = HippoWorkflowUtils.getHippoDocumentHandle(translatedDocument.getNode(getSession()));
            targetDocumentLocation = translatedDocumentHandleNode.getPath();
        } catch (RepositoryException | WorkflowException | RemoteException e) {
            log.error("Failed to trasnlate document at '{}' to '{}' in '{}'.", sourceDocumentLocation,
                    targetDocumentNodeName, targetLanguage, e);
            throw new RuntimeException("Failed to add translated document of '" + sourceDocumentLocation + "' to '"
                    + targetDocumentNodeName + "' in '" + targetLanguage + "'. " + e);
        }

        return targetDocumentLocation;
    }

    public String getDocumentWorkflowCategory() {
        return documentWorkflowCategory;
    }

    public void setDocumentWorkflowCategory(String documentWorkflowCategory) {
        this.documentWorkflowCategory = documentWorkflowCategory;
    }

    protected Session getSession() {
        return session;
    }

    protected DocumentWorkflow getDocumentWorkflow(final Node documentHandleNode) throws RepositoryException {
        return (DocumentWorkflow) HippoWorkflowUtils.getHippoWorkflow(getSession(), getDocumentWorkflowCategory(),
                documentHandleNode);
    }

    protected TranslationWorkflow getDocumentTranslationWorkflow(final Node documentVariantNode)
            throws RepositoryException {
        return (TranslationWorkflow) HippoWorkflowUtils.getHippoWorkflow(getSession(), "translation",
                documentVariantNode);
    }
}
