/*
 * Copyright 2024 Bloomreach (https://www.bloomreach.com)
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
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HippoFolderDocumentUtilsTest {

    @Mock
    private Node node;

    @Mock
    private Node parentNode;

    @Mock
    private Node rootNode;

    @Mock
    private Session session;

    @Mock
    private Property property;

    // ---- getHippoTranslationLanguage ----

    @Test
    void getHippoTranslationLanguage_whenPropertyPresent_returnsLocale() throws RepositoryException {
        when(node.hasProperty("hippotranslation:locale")).thenReturn(true);
        when(node.getProperty("hippotranslation:locale")).thenReturn(property);
        when(property.getString()).thenReturn("en");

        assertEquals("en", HippoFolderDocumentUtils.getHippoTranslationLanguage(node));
    }

    @Test
    void getHippoTranslationLanguage_whenPropertyAbsent_returnsNull() throws RepositoryException {
        when(node.hasProperty("hippotranslation:locale")).thenReturn(false);

        assertNull(HippoFolderDocumentUtils.getHippoTranslationLanguage(node));
    }

    @Test
    void getHippoTranslationLanguage_whenRepositoryExceptionThrown_returnsNull() throws RepositoryException {
        when(node.hasProperty("hippotranslation:locale")).thenThrow(new RepositoryException("jcr error"));

        assertNull(HippoFolderDocumentUtils.getHippoTranslationLanguage(node));
    }

    // ---- getHippoDocumentHandle ----

    @Test
    void getHippoDocumentHandle_whenNodeIsHandle_returnsItself() throws RepositoryException {
        when(node.isNodeType("hippo:handle")).thenReturn(true);

        assertEquals(node, HippoFolderDocumentUtils.getHippoDocumentHandle(node));
    }

    @Test
    void getHippoDocumentHandle_whenNodeIsDocumentVariantWithHandleParent_returnsParent() throws RepositoryException {
        when(node.isNodeType("hippo:handle")).thenReturn(false);
        when(node.isNodeType("hippo:document")).thenReturn(true);
        when(node.getSession()).thenReturn(session);
        when(session.getRootNode()).thenReturn(rootNode);
        when(rootNode.isSame(node)).thenReturn(false);
        when(node.getParent()).thenReturn(parentNode);
        when(parentNode.isNodeType("hippo:handle")).thenReturn(true);

        assertEquals(parentNode, HippoFolderDocumentUtils.getHippoDocumentHandle(node));
    }

    @Test
    void getHippoDocumentHandle_whenNodeIsDocumentVariantWithNonHandleParent_returnsNull() throws RepositoryException {
        when(node.isNodeType("hippo:handle")).thenReturn(false);
        when(node.isNodeType("hippo:document")).thenReturn(true);
        when(node.getSession()).thenReturn(session);
        when(session.getRootNode()).thenReturn(rootNode);
        when(rootNode.isSame(node)).thenReturn(false);
        when(node.getParent()).thenReturn(parentNode);
        when(parentNode.isNodeType("hippo:handle")).thenReturn(false);

        assertNull(HippoFolderDocumentUtils.getHippoDocumentHandle(node));
    }

    @Test
    void getHippoDocumentHandle_whenNodeIsRootDocument_returnsNull() throws RepositoryException {
        when(node.isNodeType("hippo:handle")).thenReturn(false);
        when(node.isNodeType("hippo:document")).thenReturn(true);
        when(node.getSession()).thenReturn(session);
        when(session.getRootNode()).thenReturn(rootNode);
        // node IS the root node
        when(rootNode.isSame(node)).thenReturn(true);

        assertNull(HippoFolderDocumentUtils.getHippoDocumentHandle(node));
    }

    @Test
    void getHippoDocumentHandle_whenNodeIsNeitherHandleNorDocument_returnsNull() throws RepositoryException {
        when(node.isNodeType("hippo:handle")).thenReturn(false);
        when(node.isNodeType("hippo:document")).thenReturn(false);

        assertNull(HippoFolderDocumentUtils.getHippoDocumentHandle(node));
    }

    // ---- folderExists ----

    @Test
    void folderExists_whenNodeDoesNotExist_returnsFalse() throws RepositoryException {
        when(session.nodeExists("/content/folder")).thenReturn(false);

        boolean result = HippoFolderDocumentUtils.folderExists(session, "/content/folder");

        assertEquals(false, result);
    }

    // ---- documentExists ----

    @Test
    void documentExists_whenNodeDoesNotExist_returnsFalse() throws RepositoryException {
        when(session.nodeExists("/content/doc")).thenReturn(false);

        boolean result = HippoFolderDocumentUtils.documentExists(session, "/content/doc");

        assertEquals(false, result);
    }
}
