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

import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.MetadataResourceHelper;
import java.io.StringWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.lang3.StringUtils;

public final class DataCiteHelper {

    /**
     * "Unavailable value" code per https://www.dublincore.org/groups/kernel/spec/ and https://ezid.cdlib.org/doc/apidoc.html
     */
    private static final String UNAVAILABLE_VALUE = "(:unav:)";

    private DataCiteHelper() {
    }

    /**
     * Create DataCite XML metadata per https://datacite-metadata-schema.readthedocs.io/_/downloads/en/4.5/pdf/
     */
    public static String createDataCiteXmlMetadataForVersion(String name, Entry<?, ?> entry, Version<?> version) {
        try {
            StringWriter s = new StringWriter();
            XMLOutputFactory f = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = f.createXMLStreamWriter(s);

            writer.writeStartDocument();
            writer.writeStartElement("resource");
            writer.writeAttribute("xmlns", "http://datacite.org/schema/kernel-4");
            writer.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            writer.writeAttribute("xsi:schemaLocation", "http://datacite.org/schema/kernel-4 http://schema.datacite.org/meta/kernel-4/metadata.xsd");

            writer.writeStartElement("identifier");
            writer.writeAttribute("identifierType", "DOI");
            writer.writeCharacters(name);
            writer.writeEndElement();

            writer.writeStartElement("creators");
            for (OrcidAuthor orcidAuthor: version.getOrcidAuthors()) {
                writer.writeStartElement("creator");
                writer.writeStartElement("nameIdentifier");
                writer.writeAttribute("schemeURI", "https://orcid.org/");
                writer.writeAttribute("nameIdentifierScheme", "ORCID");
                writer.writeCharacters(orcidAuthor.getOrcid());
                writer.writeEndElement();
                writer.writeEndElement();
            }
            for (Author author: version.getAuthors()) {
                writer.writeStartElement("creator");
                writer.writeStartElement("creatorName");
                writer.writeCharacters(author.getName());
                writer.writeEndElement();
                writer.writeEndElement();
            }
            writer.writeEndElement();

            writer.writeStartElement("titles");
            writer.writeStartElement("title");
            writer.writeCharacters(computePath(entry, version));
            writer.writeEndElement();
            writer.writeEndElement();

            writer.writeStartElement("descriptions");
            writer.writeStartElement("description");
            writer.writeAttribute("descriptionType", "Abstract");
            writer.writeCharacters(computeDescription(entry, version));
            writer.writeEndElement();
            writer.writeEndElement();

            writer.writeStartElement("publisher");
            writer.writeCharacters("Dockstore");
            writer.writeEndElement();

            writer.writeStartElement("publicationYear");
            writer.writeCharacters("2024"); // TODO
            writer.writeEndElement();

            writer.writeStartElement("resourceType");
            writer.writeAttribute("resourceTypeGeneral", "Workflow");
            writer.writeCharacters("Workflow");
            writer.writeEndElement();

            if (!entry.getLabels().isEmpty()) {
                writer.writeStartElement("subjects");
                for (Label label: entry.getLabels()) {
                    writer.writeStartElement("subject");
                    writer.writeCharacters(label.getValue());
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }

            writer.writeEndElement(); // end resource element
            writer.writeEndDocument();

            return s.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("unexpected xml error", e);
        }
    }

    private static String computePath(Entry<?, ?> entry, Version<?> version) {
        return MetadataResourceHelper.createVersionName(entry, version);
    }

    private static String computeDescription(Entry<?, ?> entry, Version<?> version) {
        return StringUtils.firstNonEmpty(version.getDescription(), UNAVAILABLE_VALUE);
    }
}
