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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.WorkflowException;
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
    public boolean lockDocument(String documentLocation) {
        log.debug("##### lockDocument('{}')", documentLocation);

        if (StringUtils.isBlank(documentLocation)) {
            throw new IllegalArgumentException("Invalid document location: '" + documentLocation + "'.");
        }

        boolean locked = false;

        try {
            if (!getSession().nodeExists(documentLocation)) {
                throw new IllegalArgumentException("Document doesn't exist at '" + documentLocation + "'.");
            }

            Node node = getSession().getNode(documentLocation);
            DocumentWorkflow documentWorkflow = getDocumentWorkflow(node);

            Boolean obtainEditableInstance = (Boolean) documentWorkflow.hints().get("obtainEditableInstance");

            if (BooleanUtils.isTrue(obtainEditableInstance)) {
                documentWorkflow.obtainEditableInstance();
                locked = true;
            } else {
                throw new IllegalStateException("Document at '" + documentLocation + "' is not allowed to obtain an editable instance.");
            }
        } catch (Exception e) {
            log.error("Failed to commit editable instance on document.");
            throw new RuntimeException("Failed to obtain editable instance on document at '" + documentLocation + "'. " + e);
        }

        return locked;
    }

    @Override
    public boolean unlockDocument(String documentLocation) {
        log.debug("##### unlockDocument('{}')", documentLocation);

        if (StringUtils.isBlank(documentLocation)) {
            throw new IllegalArgumentException("Invalid document location: '" + documentLocation + "'.");
        }

        boolean unlocked = false;

        try {
            if (!getSession().nodeExists(documentLocation)) {
                throw new IllegalArgumentException("Document doesn't exist at '" + documentLocation + "'.");
            }

            Node node = getSession().getNode(documentLocation);
            DocumentWorkflow documentWorkflow = getDocumentWorkflow(node);

            Boolean unlock = (Boolean) documentWorkflow.hints().get("unlock");

            if (BooleanUtils.isTrue(unlock)) {
                documentWorkflow.unlock();
                unlocked = true;
            } else {
                // let's suppose it's not locked at the moment if unclock is false, assuming this service is run by admin user.
                unlocked = true;
            }
        } catch (Exception e) {
            log.error("Failed to unlock document.");
            throw new RuntimeException("Failed to unlock document at '" + documentLocation + "'. " + e);
        }

        return unlocked;
    }

    @Override
    public String copyDocument(String sourceDocumentLocation, String targetFolderLocation, String targetDocumentName) {
        log.debug("##### copyDocument('{}', '{}', '{}')", sourceDocumentLocation, targetFolderLocation,
                targetDocumentName);

        String targetDocumentLocation = null;

        try {
            if (!getSession().nodeExists(sourceDocumentLocation)) {
                throw new IllegalArgumentException("Source document doesn't exist at '" + sourceDocumentLocation + "'.");
            }

            if (!getSession().nodeExists(targetFolderLocation)) {
                throw new IllegalArgumentException("Target folder doesn't exist at '" + targetFolderLocation + "'.");
            }

            Node sourceDocumentNode = getSession().getNode(sourceDocumentLocation);
            Node targetFolderNode = getSession().getNode(targetFolderLocation);

            DocumentWorkflow documentWorkflow = getDocumentWorkflow(sourceDocumentNode);
            documentWorkflow.copy(new Document(targetFolderNode), targetDocumentName);

            targetDocumentLocation = targetFolderNode.getNode(targetDocumentName).getPath();
        } catch (RepositoryException | WorkflowException | RemoteException e) {
            log.error("Failed to copy document at '{}' to '{}/{}'.", sourceDocumentLocation, targetFolderLocation,
                    targetDocumentName, e);
            throw new RuntimeException("Failed to copy document at '" + sourceDocumentLocation + "' to '"
                    + targetFolderLocation + "/" + targetDocumentName + "'. " + e);
        }

        return targetDocumentLocation;
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

            Node node = getSession().getNode(documentLocation);
            DocumentWorkflow documentWorkflow = getDocumentWorkflow(node);

            Boolean isLive = (Boolean) documentWorkflow.hints().get("isLive");

            if (BooleanUtils.isFalse(isLive)) {
                // already offline, so just return true
                depublished = true;
            } else {
                Boolean depublish = (Boolean) documentWorkflow.hints().get("depublish");

                if (!BooleanUtils.isTrue(depublish)) {
                    throw new IllegalStateException("Document at '" + documentLocation + "' doesn't have depublish action.");
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

            Node node = getSession().getNode(documentLocation);
            DocumentWorkflow documentWorkflow = getDocumentWorkflow(node);

            Boolean isLive = (Boolean) documentWorkflow.hints().get("isLive");

            if (BooleanUtils.isTrue(isLive)) {
                // already published, so just return true
                published = true;
            } else {
                Boolean previewAvailable = (Boolean) documentWorkflow.hints().get("previewAvailable");

                if (!BooleanUtils.isTrue(previewAvailable)) {
                    throw new IllegalStateException("Document at '" + documentLocation + "' doesn't have preview variant.");
                }

                Boolean publish = (Boolean) documentWorkflow.hints().get("publish");

                if (!BooleanUtils.isTrue(publish)) {
                    throw new IllegalStateException("Document at '" + documentLocation + "' doesn't have publish action.");
                }

                documentWorkflow.publish();
                published = true;
            }
        } catch (RepositoryException | WorkflowException | RemoteException e) {
            log.error("Failed to publish document at '{}'.", documentLocation, e);
            throw new RuntimeException("Failed to publish document at '" + documentLocation + "'. " + e);
        }

        return published;
    }

    protected Session getSession() {
        return session;
    }

    protected String getDocumentWorkflowCategory() {
        return documentWorkflowCategory;
    }

    protected DocumentWorkflow getDocumentWorkflow(final Node node) throws RepositoryException {
        return (DocumentWorkflow) HippoWorkflowUtils.getWorkflow(getSession(), getDocumentWorkflowCategory(), node);
    }
}
