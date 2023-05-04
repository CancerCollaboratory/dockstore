/*
 *    Copyright 2022 OICR and UCSC
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
 */

package io.dockstore.webservice.core;

import java.io.Serializable;
import java.util.Objects;

public class OrcidAuthorInformation extends Author implements Serializable {

    private String orcid;

    public OrcidAuthorInformation(String orcid) {
        this.orcid = orcid;
    }

    public String getOrcid() {
        return this.orcid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrcidAuthorInformation that)) {
            return false;
        }

        return Objects.equals(orcid, that.orcid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orcid);
    }
}
