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

public interface DocumentManagementServiceMXBean {

    String NAME = "org.onehippo.forge.channelmanager.pagesupport.document.management:type=DocumentManagementServiceMXBean";

    boolean obtainEditableDocument(String documentLocation);

    boolean disposeEditableDocument(String documentLocation);

    boolean commitEditableDocument(String documentLocation);

    boolean depublishDocument(String documentLocation);

    boolean publishDocument(String documentLocation);

    String copyDocument(String sourceDocumentLocation, String targetFolderLocation, String targetDocumentName);

    String translateFolder(String sourceFolderLocation, String language, String name);

    String translateDocument(String sourceDocumentLocation, String language, String name);

}
