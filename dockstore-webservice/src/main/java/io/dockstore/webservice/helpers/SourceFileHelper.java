/*
 * Copyright 2023 OICR and UCSC
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
 *
 */

package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage.FileTypeCategory;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SourceFileHelper {

    private SourceFileHelper() {

    }

    public static Optional<SourceFile> findPrimaryDescriptor(WorkflowVersion workflowVersion) {
        return workflowVersion.getSourceFiles().stream()
            .filter(sf -> Objects.equals(sf.getPath(), workflowVersion.getWorkflowPath()))
            .findFirst();
    }

    public static List<SourceFile> findTestFiles(WorkflowVersion workflowVersion) {
        return workflowVersion.getSourceFiles().stream()
            .filter(sf -> sf.getType().getCategory().equals(FileTypeCategory.TEST_FILE))
            .toList();
    }
}
