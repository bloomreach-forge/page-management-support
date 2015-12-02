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

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.commons.lang.BooleanUtils;

class DocumentManagementServiceClient {

    private static final String DEFAULT_DOCUMENT_MANAGEMENT_SERVICE_NAME = "org.onehippo.forge.channelmanager.pagesupport.document.management:type=DocumentManagementServiceMXBean";

    private ObjectName mbeanName;
    private MBeanServer mbeanServer;

    public boolean obtainEditableDocument(String documentLocation) throws Exception {
        Boolean ret = (Boolean) invokeDocumentManagementServiceMBean("obtainEditableDocument",
                new String[] { documentLocation }, new String[] { String.class.getName() });
        return BooleanUtils.isTrue(ret);
    }

    public boolean disposeEditableDocument(String documentLocation) throws Exception {
        Boolean ret = (Boolean) invokeDocumentManagementServiceMBean("disposeEditableDocument",
                new String[] { documentLocation }, new String[] { String.class.getName() });
        return BooleanUtils.isTrue(ret);
    }

    public boolean commitEditableDocument(String documentLocation) throws Exception {
        Boolean ret = (Boolean) invokeDocumentManagementServiceMBean("commitEditableDocument",
                new String[] { documentLocation }, new String[] { String.class.getName() });
        return BooleanUtils.isTrue(ret);
    }

    public String copyDocument(String sourceDocumentLocation, String targetFolderLocation, String targetDocumentName) throws Exception {
        String ret = (String) invokeDocumentManagementServiceMBean("copyDocument",
                new String[] { sourceDocumentLocation, targetFolderLocation, targetDocumentName },
                new String[] { String.class.getName(), String.class.getName(), String.class.getName() });
        return ret;
    }

    boolean depublishDocument(String documentLocation) throws Exception {
        Boolean ret = (Boolean) invokeDocumentManagementServiceMBean("depublishDocument",
                new String[] { documentLocation }, new String[] { String.class.getName() });
        return BooleanUtils.isTrue(ret);
    }

    public boolean publishDocument(String documentLocation) throws Exception {
        Boolean ret = (Boolean) invokeDocumentManagementServiceMBean("publishDocument",
                new String[] { documentLocation }, new String[] { String.class.getName() });
        return BooleanUtils.isTrue(ret);
    }

    public ObjectName getMbeanName() {
        if (mbeanName == null) {
            try {
                mbeanName = new ObjectName(DEFAULT_DOCUMENT_MANAGEMENT_SERVICE_NAME);
            } catch (MalformedObjectNameException e) {
                throw new IllegalStateException(e.toString(), e);
            }
        }

        return mbeanName;
    }

    public void setMbeanName(ObjectName mbeanName) {
        this.mbeanName = mbeanName;
    }

    public MBeanServer getMbeanServer() {
        if (mbeanServer == null) {
            mbeanServer = ManagementFactory.getPlatformMBeanServer();
        }

        return mbeanServer;
    }

    public void setMbeanServer(MBeanServer mbeanServer) {
        this.mbeanServer = mbeanServer;
    }

    private Object invokeDocumentManagementServiceMBean(String operationName, Object[] params, String[] signature)
            throws Exception {
        return getMbeanServer().invoke(getMbeanName(), operationName, params, signature);
    }

}
