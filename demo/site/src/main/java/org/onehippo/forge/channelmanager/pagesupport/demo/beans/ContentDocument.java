package org.onehippo.forge.channelmanager.pagesupport.demo.beans;
/*
 * Copyright 2014-2015 Hippo B.V. (http://www.onehippo.com)
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
import java.util.Calendar;

import org.hippoecm.hst.content.beans.Node;
import org.hippoecm.hst.content.beans.standard.HippoHtml;
import org.onehippo.cms7.essentials.dashboard.annotations.HippoEssentialsGenerated;

@HippoEssentialsGenerated(internalName = "channelpagesupdemo:contentdocument")
@Node(jcrType = "channelpagesupdemo:contentdocument")
public class ContentDocument extends BaseDocument {
    @HippoEssentialsGenerated(internalName = "channelpagesupdemo:introduction")
    public String getIntroduction() {
        return getProperty("channelpagesupdemo:introduction");
    }

    @HippoEssentialsGenerated(internalName = "channelpagesupdemo:title")
    public String getTitle() {
        return getProperty("channelpagesupdemo:title");
    }

    @HippoEssentialsGenerated(internalName = "channelpagesupdemo:content")
    public HippoHtml getContent() {
        return getHippoHtml("channelpagesupdemo:content");
    }

    @HippoEssentialsGenerated(internalName = "channelpagesupdemo:publicationdate")
    public Calendar getPublicationDate() {
        return getProperty("channelpagesupdemo:publicationdate");
    }
}
