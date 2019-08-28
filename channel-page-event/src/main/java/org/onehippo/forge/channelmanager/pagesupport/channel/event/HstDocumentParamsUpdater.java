/*
 * Copyright 2017-2019 Bloomreach B.V. (http://www.bloomreach.com)
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang.StringUtils;

import org.hippoecm.hst.configuration.ConfigurationUtils;
import org.hippoecm.hst.configuration.HstNodeTypes;
import org.hippoecm.hst.configuration.components.HstComponentConfiguration;
import org.hippoecm.hst.configuration.hosting.Mount;
import org.hippoecm.hst.content.beans.ObjectBeanManagerException;
import org.hippoecm.hst.content.beans.standard.HippoAvailableTranslationsBean;
import org.hippoecm.hst.content.beans.standard.HippoDocument;
import org.hippoecm.hst.content.beans.standard.HippoDocumentBean;
import org.hippoecm.hst.core.linking.DocumentParamsScanner;
import org.hippoecm.hst.core.request.HstRequestContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility inspired by {@link org.hippoecm.hst.core.linking.DocumentParamsScanner}.
 *
 * It scans HST configuration and fixes broken 'documentLink' and 'jcrPath' parameters that point to non-existing paths.
 */
public final class HstDocumentParamsUpdater {

    private static Logger log = LoggerFactory.getLogger(HstDocumentParamsUpdater.class);

    private HstDocumentParamsUpdater() {
    }

    /**
     * Update 'documentLink' and 'jcrPath' HST parameters in the target HST configuration node, based on the source and
     * target documents being linked as translations of each other.
     */
    public static void updateTargetDocumentPaths(final Mount sourceMount,
                                                final HstComponentConfiguration source,
                                                final Mount targetMount,
                                                final Node targetNode,
                                                final HstRequestContext requestContext) {
        try {
            final Set<String> parameters = DocumentParamsScanner.getNames(source, DocumentCopyingPageCopyEventListener.class.getClassLoader());
            log.debug("Got document parameters {} from component {}", parameters, source.getCanonicalStoredLocation());

            if (!parameters.isEmpty()) {
                final Map<String, String> changeMap = getTargetDocumentPaths(sourceMount, source, targetMount, requestContext, parameters);
                replaceTargetParameterValues(targetNode, changeMap);
            }

            // recursively update child nodes, based on the target node names because the source is merged configuration
            // so can have other (inherited) children
            final NodeIterator targetChildren = targetNode.getNodes();

            while (targetChildren.hasNext()) {

                final Node targetChild = targetChildren.nextNode();
                final HstComponentConfiguration sourceChild = source.getChildByName(targetChild.getName());
                if (sourceChild == null) {
                    log.warn("No child named {} found for source configuration, skipping updating {} and below", targetChild.getName(), targetChild.getPath());
                } else {
                    // recursion
                    updateTargetDocumentPaths(sourceMount, sourceChild, targetMount, targetChild, requestContext);
                }
            }
        } catch (RepositoryException e) {
            log.error("RepositoryException updating HST configuration", e);
        }
    }

    /**
     * Replace all matching parameter values with changed ones.
     */
    public static void replaceTargetParameterValues(final Node targetNode,
                                                    final Map<String, String> changeMap) throws RepositoryException {

        if (targetNode.hasProperty(HstNodeTypes.GENERAL_PROPERTY_PARAMETER_VALUES)) {
            final Value[] paramValues = targetNode.getProperty(HstNodeTypes.GENERAL_PROPERTY_PARAMETER_VALUES).getValues();
            final String[] oldValues = new String[paramValues.length];
            for (int i = 0; i < paramValues.length; i++) {
                oldValues[i] = paramValues[i].getString();
            }

            final String[] newValues = new String[oldValues.length];
            boolean changed = false;
            for (int j = 0; j < oldValues.length; j++) {
                if (changeMap.containsKey(oldValues[j])) {
                    newValues[j] = changeMap.get(oldValues[j]);
                    changed = true;
                } else {
                    newValues[j] = oldValues[j];
                }
            }

            if (changed) {
                log.debug("Updating property {}/{} from {} to {}", targetNode.getPath(), HstNodeTypes.GENERAL_PROPERTY_PARAMETER_VALUES, oldValues, newValues);
                targetNode.setProperty(HstNodeTypes.GENERAL_PROPERTY_PARAMETER_VALUES, newValues);
            }
        }
    }

    /**
     * Map source content paths to target content paths, based on translated (linked) content
     */
    public static Map<String, String> getTargetDocumentPaths(final Mount sourceMount,
                                                             final HstComponentConfiguration source,
                                                             final Mount targetMount,
                                                             final HstRequestContext requestContext,
                                                             final Set<String> parameters) {

        final Map<String, String> changeMap = new HashMap<>();

        for (final String parameter : parameters) {

            // regular parameters
            final String sourceDocumentPath = source.getParameter(parameter);
            if (StringUtils.isNotEmpty(sourceDocumentPath)) {
                final String targetDocumentPath = getTargetDocumentPath(sourceMount.getContentPath(), sourceDocumentPath, targetMount.getContentPath(), requestContext);
                if (!sourceDocumentPath.equals(targetDocumentPath)) {
                    changeMap.put(sourceDocumentPath, targetDocumentPath);
                }
            }

            // variant parameters (relevance configuration)
            for (final String prefix : source.getParameterPrefixes()) {
                final String prefixedParam = ConfigurationUtils.createPrefixedParameterName(prefix, parameter);
                final String variantSourceDocumentPath = source.getParameter(prefixedParam);
                if (StringUtils.isNotEmpty(variantSourceDocumentPath)) {
                    final String targetDocumentPath = getTargetDocumentPath(sourceMount.getContentPath(), variantSourceDocumentPath, targetMount.getContentPath(), requestContext);
                    if (!variantSourceDocumentPath.equals(targetDocumentPath)) {
                        changeMap.put(variantSourceDocumentPath, targetDocumentPath);
                    }
                }
            }
        }

        log.debug("Mapped parameter values: {}", changeMap);
        return changeMap;
    }

    /**
     * Get a target document path from a source, based on the linked translations and target base content path.
     */
    public static String getTargetDocumentPath(final String sourceMountContentPath,
                                               final String sourceDocumentPath,
                                               final String targetMountContentPath,
                                               final HstRequestContext requestContext) {

        final boolean isAbsolute = sourceDocumentPath.startsWith("/");

        final String sourceAbsolutePath = isAbsolute ? sourceDocumentPath : sourceMountContentPath + '/' + sourceDocumentPath;
        ClassLoader currentCL = null ,objCL = null;
        try {
            Object obj = requestContext.getObjectBeanManager().getObject(sourceAbsolutePath);
            currentCL = Thread.currentThread().getContextClassLoader();
            objCL = obj.getClass().getClassLoader();
            Class<?> clazz = HippoDocument.class;
            if( currentCL != objCL){
                Thread.currentThread().setContextClassLoader(objCL);
                clazz = Thread.currentThread().getContextClassLoader().loadClass(HippoDocument.class.getName());
            }
            if (obj.getClass().isAssignableFrom(clazz)) {

                final HippoAvailableTranslationsBean<HippoDocumentBean> translations = ((HippoDocumentBean) obj).getAvailableTranslations();
                for (final HippoDocumentBean bean : translations.getTranslations()) {

                    if (bean.getPath().startsWith(targetMountContentPath)) {

                        // take the full handle path if absolute, else subtract targetMountContentPath/
                        final String targetDocumentPath = isAbsolute ? bean.getCanonicalHandlePath() :
                                bean.getCanonicalHandlePath().substring(targetMountContentPath.length() + 1);
                        log.debug("Determined target path {} based on source path {}", targetDocumentPath, sourceDocumentPath);
                        return targetDocumentPath;
                    }
                }
            } else {
                log.warn("Object for path {} is not a HippoDocument but {}", sourceAbsolutePath, obj.getClass().getName());
            }
        } catch (ObjectBeanManagerException e) {
            log.error("Failed to get a bean from path {}", sourceAbsolutePath, e);
        } catch (ClassNotFoundException e) {
            log.error("Error during reload of class HippoDocument due to different class loaders",e);
        } finally {
            if( currentCL != objCL) {
                Thread.currentThread().setContextClassLoader(currentCL);
            }
        }

        // fallback to source, may leave broken configuration paths
        return sourceMountContentPath;
    }
}
