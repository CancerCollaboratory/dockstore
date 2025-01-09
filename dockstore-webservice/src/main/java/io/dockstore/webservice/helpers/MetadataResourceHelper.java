/*    Copyright 2022 OICR, UCSC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;

public final class MetadataResourceHelper {

    private static String baseUrl;

    private MetadataResourceHelper() {
    }

    public static void init(DockstoreWebserviceConfiguration config) {
        baseUrl = config.getExternalConfig().computeBaseUrl();
    }

    public static String createEntryName(Entry<?, ?> entry) {
        return entry.getEntryPath();
    }

    public static String createVersionName(Entry<?, ?> entry, Version<?> version) {
        return createEntryName(entry) + ":" + version.getName();
    }

    public static String createEntryURL(Entry<?, ?> entry) {
        return baseUrl + "/" + entry.getEntryTypeMetadata().getSitePath() + "/" + createEntryName(entry);
    }

    public static String createVersionURL(Entry<?, ?> entry, Version<?> version) {
        return createEntryURL(entry) + ":" + version.getName();
    }

    public static String createOrganizationURL(Organization organization) {
        return baseUrl + "/organizations/" + organization.getName();
    }

    public static String createCollectionURL(Collection collection, Organization organization) {
        return baseUrl + "/organizations/" + organization.getName() + "/collections/"  + collection.getName();
    }

    public static String createToolURL(Tool tool) {
        return createEntryURL(tool);
    }

    public static String createWorkflowURL(Workflow workflow) {
        return createEntryURL(workflow);
    }
}
