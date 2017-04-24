package org.onehippo.forge.channelmanager.pagesupport.demo.listener;

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
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyContext;
import org.hippoecm.hst.pagecomposer.jaxrs.api.PageCopyEvent;
import org.onehippo.forge.channelmanager.pagesupport.channel.event.DocumentCopyingPageCopyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A  PageCopyEventListener that implements the hook described at
 * https://onehippo-forge.github.io/page-management-support/custom-configuration.html#Extension_Hooks
 * 
 * It scans HST configuration and fixes broken 'documentLink' and 'jcrPath' parameters that point to non-existing paths.
 */
public class ConfigUpdatingPageCopyEventListener extends DocumentCopyingPageCopyEventListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigUpdatingPageCopyEventListener.class);

    @Override
    protected void onAfterPageCopyEvent(PageCopyEvent pageCopyEvent) {

        final PageCopyContext pageCopyContext = pageCopyEvent.getPageCopyContext();

        updateDocumentPaths(pageCopyContext.getEditingMount(), pageCopyContext.getSourcePage(),
                pageCopyContext.getTargetMount(), pageCopyContext.getNewPageNode(),
                pageCopyContext.getRequestContext());

    }

    protected void updateDocumentPaths(final Mount sourceMount,
                                            final HstComponentConfiguration source,
                                            final Mount targetMount,
                                            final Node targetNode,
                                            final HstRequestContext requestContext) {

        try {
            final String componentClassName = source.getComponentClassName();

            if (StringUtils.isNotEmpty(componentClassName)) {

                final Set<String> parameters = DocumentParamsScanner.getNames(componentClassName, ConfigUpdatingPageCopyEventListener.class.getClassLoader());
                log.debug("Got document parameters {} from component class {}", parameters, componentClassName);

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
                }
                else {
                    // recursion
                    updateDocumentPaths(sourceMount, sourceChild, targetMount, targetChild, requestContext);
                }
            }
        } catch (RepositoryException e) {
            log.error("RepositoryException updating HST configuration", e);
        }
    }

    /**
     * Replace all matching parameter values with the changed ones.
     */
    protected static void replaceTargetParameterValues(final Node targetNode, final Map<String, String> changeMap) throws RepositoryException {

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
                }
                else {
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
    protected Map<String, String> getTargetDocumentPaths(final Mount sourceMount, final HstComponentConfiguration source, final Mount targetMount, final HstRequestContext requestContext, final Set<String> parameters) {

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

    protected String getTargetDocumentPath(final String sourceMountContentPath, final String sourceDocumentPath, final String targetMountContentPath, final HstRequestContext requestContext) {

        final boolean isAbsolute = sourceDocumentPath.startsWith("/");

        final String sourceAbsolutePath = isAbsolute ? sourceDocumentPath : sourceMountContentPath + "/" + sourceDocumentPath;
        try {
            Object obj = requestContext.getObjectBeanManager().getObject(sourceAbsolutePath);
            if (obj instanceof HippoDocument) {

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
            }
            else {
                log.error("Object for path {} is not a HippoDocument but a {}", sourceAbsolutePath, obj.getClass().getName());
            }
        } catch (ObjectBeanManagerException e) {
            log.error("Failed to get a bean from path " + sourceAbsolutePath, e);
        }

        // fallback to source, may leave broken configuration paths
        return sourceMountContentPath;
    }
}
