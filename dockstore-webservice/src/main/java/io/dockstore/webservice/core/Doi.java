/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Schema(description = "A Digital Object Identifier (DOI)")
@Table(name = "doi", uniqueConstraints = @UniqueConstraint(name = "unique_doi_name", columnNames = { "name" }))
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Doi.findByName", query = "SELECT d FROM Doi d WHERE :doiName = d.name")
})
public class Doi {
    // DOI order of precedence from greatest to least
    public static final List<DoiInitiator> DOI_ORDER_OF_PRECEDENCE = List.of(DoiInitiator.USER, DoiInitiator.GITHUB, DoiInitiator.CUSTOM, DoiInitiator.DOCKSTORE);
    public static final int MAX_NUMBER_OF_DOI_INITIATORS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Implementation specific ID for the DOI in this web service")
    private long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "The type of DOI", requiredMode = RequiredMode.REQUIRED, example = "CONCEPT")
    private DoiType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "The initiator that initiated the creation of the DOI", requiredMode = RequiredMode.REQUIRED, example = "USER")
    private DoiInitiator initiator;

    @Column(nullable = false)
    @Schema(description = "The DOI name", requiredMode = RequiredMode.REQUIRED, example = "10.5281/zenodo.705645")
    private String name;

    @Column
    @JsonIgnore
    private String editAccessLinkId;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Doi() {
    }

    public Doi(DoiType type, DoiInitiator initiator, String name) {
        this.type = type;
        this.initiator = initiator;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public DoiType getType() {
        return type;
    }

    public void setType(DoiType type) {
        this.type = type;
    }

    public DoiInitiator getInitiator() {
        return initiator;
    }

    public void setInitiator(DoiInitiator initiator) {
        this.initiator = initiator;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEditAccessLinkId() {
        return editAccessLinkId;
    }

    public void setEditAccessLinkId(String editAccessLinkId) {
        this.editAccessLinkId = editAccessLinkId;
    }

    public static Doi getDoiBasedOnOrderOfPrecedence(Map<DoiInitiator, Doi> dois) {
        for (DoiInitiator doiInitiator : DOI_ORDER_OF_PRECEDENCE) {
            if (dois.containsKey(doiInitiator)) {
                return dois.get(doiInitiator);
            }
        }
        return null;
    }

    public enum DoiType {
        CONCEPT,
        VERSION
    }

    public enum DoiInitiator {
        USER,
        DOCKSTORE,
        GITHUB,
        CUSTOM
    }
}
