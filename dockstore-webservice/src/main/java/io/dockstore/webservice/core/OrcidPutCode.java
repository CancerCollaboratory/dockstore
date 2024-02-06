package io.dockstore.webservice.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.sql.Timestamp;

@Embeddable
@Schema(description = "An ORCID put code uniquely identifies a work on ORCID")
public class OrcidPutCode implements Serializable {

    @Column(columnDefinition = "text")
    public String orcidPutCode;

    // database timestamps
    @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    private Timestamp dbCreateDate;

    // There is no dbupdatedate because it doesn't work with @Embeddable nor @ElementCollection

    public OrcidPutCode() {}

    public OrcidPutCode(String orcidPutCode) {
        this.orcidPutCode = orcidPutCode;
    }
}
