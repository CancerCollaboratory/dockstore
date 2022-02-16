/*
 * Copyright 2021 OICR and UCSC
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

package io.dockstore.webservice.jdbi;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.AppTool;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.hibernate.SessionFactory;

public class AppToolDAO extends EntryDAO<AppTool> {
    public AppToolDAO(SessionFactory factory) {
        super(factory);
    }

    @Override
    @SuppressWarnings({"checkstyle:ParameterNumber"})
    protected Root<AppTool> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author,
        Boolean checker, CriteriaBuilder cb, CriteriaQuery<?> q) {
        throw new UnsupportedOperationException("Only supported for BioWorkflow and Tools");
    }

    public List<AppTool> finalAllPublished() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.AppTool.findAllPublished"));
    }
}