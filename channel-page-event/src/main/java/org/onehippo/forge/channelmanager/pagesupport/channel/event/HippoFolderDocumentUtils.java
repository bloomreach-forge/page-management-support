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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.hippoecm.hst.util.NodeUtils;
import org.hippoecm.repository.HippoStdNodeType;
import org.hippoecm.repository.api.HippoNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hippo Folder/Document Node Utilities.
 */
public class HippoFolderDocumentUtils {

    private static Logger log = LoggerFactory.getLogger(HippoFolderDocumentUtils.class);

    private HippoFolderDocumentUtils() {
    }

    /**
     * Returns true if a folder exists at {@code folderLocation}.
     * @param session JCR session
     * @param folderLocation folder location
     * @return true if a folder exists at {@code folderLocation}
     * @throws RepositoryException if repository exception occurs
     */
    public static boolean folderExists(final Session session, final String folderLocation) throws RepositoryException {
        if (!session.nodeExists(folderLocation)) {
            return false;
        }

        final Node node = session.getNode(folderLocation);

        if (NodeUtils.isNodeType(node, HippoStdNodeType.NT_FOLDER)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if a document exists at {@code documentLocation}.
     * @param session JCR session
     * @param documentLocation document handle or variant location
     * @return true if a document exists at {@code documentLocation}
     * @throws RepositoryException if repository exception occurs
     */
    public static boolean documentExists(final Session session, final String documentLocation) throws RepositoryException {
        if (!session.nodeExists(documentLocation)) {
            return false;
        }

        final Node node = session.getNode(documentLocation);

        if (NodeUtils.isNodeType(node, HippoNodeType.NT_HANDLE)) {
            return true;
        } else if (NodeUtils.isNodeType(node, HippoNodeType.NT_DOCUMENT)) {
            if (!session.getRootNode().isSame(node)) {
                Node parentNode = node.getParent();

                if (NodeUtils.isNodeType(parentNode, HippoNodeType.NT_HANDLE)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns {@code hippotranslation:locale} property value from the {@code node} if exists,
     * or null if not existing.
     * @param node JCR node
     * @return {@code hippotranslation:locale} property value from the {@code node} if exists, or null if not existing
     */
    public static String getHippoTranslationLanguage(final Node node) {
        try {
            if (node.hasProperty("hippotranslation:locale")) {
                return node.getProperty("hippotranslation:locale").getString();
            }
        } catch (RepositoryException e) {
            log.error("Failed to retrieve hippotranslation:locale property.", e);
        }

        return null;
    }

    /**
     * Returns {@code node} if it is a document handle node, or its parent if it is a document variant node.
     * Otherwise, returns null.
     * @param node JCR node
     * @return {@code node} if it is a document handle node, or its parent if it is a document variant node. Otherwise, returns null.
     * @throws RepositoryException if repository exception occurs
     */
    public static Node getHippoDocumentHandle(Node node) throws RepositoryException {
        if (node.isNodeType("hippo:handle")) {
            return node;
        } else if (node.isNodeType("hippo:document")) {
            if (!node.getSession().getRootNode().isSame(node)) {
                Node parentNode = node.getParent();

                if (parentNode.isNodeType("hippo:handle")) {
                    return parentNode;
                }
            }
        }

        return null;
    }
}
