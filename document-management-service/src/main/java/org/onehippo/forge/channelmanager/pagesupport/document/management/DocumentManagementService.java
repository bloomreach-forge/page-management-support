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
package org.onehippo.forge.channelmanager.pagesupport.document.management;

import javax.jcr.Session;

import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.cms7.services.SingletonService;

/**
 * Hippo CMS Document/Folder Workflow invocation service interface
 * to be reigstred in {@link HippoServiceRegistry}.
 */
@SingletonService
public interface DocumentManagementService extends DocumentManagementServiceMXBean {

    /**
     * Initializes this document management service.
     * @param session JCR session to use in workflow operations
     * @throws RuntimeException if any repository/workflow related exception occurs.
     */
    public void initialize(Session session) throws RuntimeException;

    /**
     * Destroys this document management service.
     * @throws RuntimeException if any repository/workflow related exception occurs.
     */
    public void destroy() throws RuntimeException;

}
