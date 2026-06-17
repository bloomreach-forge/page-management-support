package org.onehippo.forge.channelmanager.pagesupport.document.management.impl;

import org.hippoecm.repository.HippoStdNodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HippoWorkflowUtilsTest {

    @Mock private Node handleNode;
    @Mock private Node variantPublished;
    @Mock private Node variantUnpublished;
    @Mock private NodeIterator variantIterator;
    @Mock private Property stateProperty;
    @Mock private Value stateValue;
    @Mock private Session session;
    @Mock private Node rootNode;

    // --- getDocumentVariantsMap ---

    @Test
    void getDocumentVariantsMap_withPublishedAndUnpublished_returnsBothVariants() throws RepositoryException {
        when(handleNode.getName()).thenReturn("my-document");
        when(handleNode.getNodes("my-document")).thenReturn(variantIterator);
        when(variantIterator.hasNext()).thenReturn(true, true, false);
        when(variantIterator.nextNode()).thenReturn(variantPublished, variantUnpublished);

        when(variantPublished.hasProperty(HippoStdNodeType.HIPPOSTD_STATE)).thenReturn(true);
        when(variantPublished.getProperty(HippoStdNodeType.HIPPOSTD_STATE)).thenReturn(stateProperty);
        when(stateProperty.getString()).thenReturn("published");

        Property unpubProp = mock(Property.class);
        when(variantUnpublished.hasProperty(HippoStdNodeType.HIPPOSTD_STATE)).thenReturn(true);
        when(variantUnpublished.getProperty(HippoStdNodeType.HIPPOSTD_STATE)).thenReturn(unpubProp);
        when(unpubProp.getString()).thenReturn("unpublished");

        Map<String, Node> result = HippoWorkflowUtils.getDocumentVariantsMap(handleNode);

        assertEquals(2, result.size());
        assertSame(variantPublished, result.get("published"));
        assertSame(variantUnpublished, result.get("unpublished"));
    }

    @Test
    void getDocumentVariantsMap_withNoVariants_returnsEmptyMap() throws RepositoryException {
        when(handleNode.getName()).thenReturn("my-document");
        when(handleNode.getNodes("my-document")).thenReturn(variantIterator);
        when(variantIterator.hasNext()).thenReturn(false);

        assertTrue(HippoWorkflowUtils.getDocumentVariantsMap(handleNode).isEmpty());
    }

    @Test
    void getDocumentVariantsMap_variantWithoutStateProperty_notIncluded() throws RepositoryException {
        when(handleNode.getName()).thenReturn("my-document");
        when(handleNode.getNodes("my-document")).thenReturn(variantIterator);
        when(variantIterator.hasNext()).thenReturn(true, false);
        when(variantIterator.nextNode()).thenReturn(variantPublished);
        when(variantPublished.hasProperty(HippoStdNodeType.HIPPOSTD_STATE)).thenReturn(false);

        assertTrue(HippoWorkflowUtils.getDocumentVariantsMap(handleNode).isEmpty());
    }

    // --- getHippoDocumentHandle ---

    @Test
    void getHippoDocumentHandle_whenNodeIsHandle_returnsItself() throws RepositoryException {
        when(handleNode.isNodeType("hippo:handle")).thenReturn(true);

        assertSame(handleNode, HippoWorkflowUtils.getHippoDocumentHandle(handleNode));
    }

    @Test
    void getHippoDocumentHandle_whenNodeIsDocumentVariant_returnsParent() throws RepositoryException {
        Node variant = mock(Node.class);
        Node parent = mock(Node.class);
        when(variant.isNodeType("hippo:handle")).thenReturn(false);
        when(variant.isNodeType("hippo:document")).thenReturn(true);
        when(variant.getSession()).thenReturn(session);
        when(session.getRootNode()).thenReturn(rootNode);
        when(rootNode.isSame(variant)).thenReturn(false);
        when(variant.getParent()).thenReturn(parent);
        when(parent.isNodeType("hippo:handle")).thenReturn(true);

        assertSame(parent, HippoWorkflowUtils.getHippoDocumentHandle(variant));
    }

    @Test
    void getHippoDocumentHandle_whenNodeIsNeitherHandleNorDocument_returnsNull() throws RepositoryException {
        Node plainNode = mock(Node.class);
        when(plainNode.isNodeType("hippo:handle")).thenReturn(false);
        when(plainNode.isNodeType("hippo:document")).thenReturn(false);

        assertNull(HippoWorkflowUtils.getHippoDocumentHandle(plainNode));
    }

    @Test
    void getHippoDocumentHandle_whenDocumentIsRootNode_returnsNull() throws RepositoryException {
        Node docNode = mock(Node.class);
        when(docNode.isNodeType("hippo:handle")).thenReturn(false);
        when(docNode.isNodeType("hippo:document")).thenReturn(true);
        when(docNode.getSession()).thenReturn(session);
        when(session.getRootNode()).thenReturn(rootNode);
        when(rootNode.isSame(docNode)).thenReturn(true);

        assertNull(HippoWorkflowUtils.getHippoDocumentHandle(docNode));
    }

    @Test
    void getHippoDocumentHandle_whenParentIsNotHandle_returnsNull() throws RepositoryException {
        Node docNode = mock(Node.class);
        Node parent = mock(Node.class);
        when(docNode.isNodeType("hippo:handle")).thenReturn(false);
        when(docNode.isNodeType("hippo:document")).thenReturn(true);
        when(docNode.getSession()).thenReturn(session);
        when(session.getRootNode()).thenReturn(rootNode);
        when(rootNode.isSame(docNode)).thenReturn(false);
        when(docNode.getParent()).thenReturn(parent);
        when(parent.isNodeType("hippo:handle")).thenReturn(false);

        assertNull(HippoWorkflowUtils.getHippoDocumentHandle(docNode));
    }
}
