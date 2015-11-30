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

import javax.jcr.Session;

import org.onehippo.forge.channelmanager.pagesupport.document.management.DocumentManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentWorkflowDocumentManagementService implements DocumentManagementService {

    private static Logger log = LoggerFactory.getLogger(DocumentWorkflowDocumentManagementService.class);

    private Session session;

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
        return false;
    }

    @Override
    public boolean unlockDocument(String documentLocation) {
        log.debug("##### unlockDocument('{}')", documentLocation);
        return false;
    }

    @Override
    public String copyDocument(String sourceDocumentLocation, String targetFolderLocation, String targetDocumentName) {
        log.debug("##### copyDocument('{}', '{}', '{}')", sourceDocumentLocation, targetFolderLocation,
                targetDocumentName);
        return null;
    }

    @Override
    public boolean depublishDocument(String documentLocation) {
        log.debug("##### depublishDocument('{}')", documentLocation);
        return false;
    }

    @Override
    public boolean publishDocument(String documentLocation) {
        log.debug("##### publishDocument('{}')", documentLocation);
        return false;
    }

    protected Session getSession() {
        return session;
    }

}
