/*
 * Copyright 2024 OICR and UCSC
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
package io.dockstore.webservice.helpers.doi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyDoiService implements DoiService {

    private static final Logger LOG = LoggerFactory.getLogger(DummyDoiService.class);

    public String createDoi(String name, String url, String metadata) {
        LOG.info("would have created DOI %s linked to %s with metadata %s".formatted(name, url, metadata));
        return name;
    }
}
