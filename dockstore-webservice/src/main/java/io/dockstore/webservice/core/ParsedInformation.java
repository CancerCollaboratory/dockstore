package io.dockstore.webservice.core;

import io.dockstore.common.DescriptorLanguage;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.sql.Timestamp;

/**
 * This is for information gained after parsing the workflow with a language parser (WDLHandler, CWLHandler, etc)
 *
 * Putting DescriptorLanguage here instead of using a map with DescriptorLanguage because there may be cases where the language is
 * irrelevant. For example, workflows only have one language.
 */
@Embeddable
public class ParsedInformation {
    @Enumerated(EnumType.STRING)
    private DescriptorLanguage descriptorLanguage;
    private boolean hasHTTPImports = false;
    private boolean hasLocalImports = false;

    @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    // There is no dbupdatedate because it doesn't work with @Embeddable nor @ElementCollection

    public DescriptorLanguage getDescriptorLanguage() {
        return descriptorLanguage;
    }

    public void setDescriptorLanguage(DescriptorLanguage descriptorLanguage) {
        this.descriptorLanguage = descriptorLanguage;
    }

    public boolean isHasHTTPImports() {
        return hasHTTPImports;
    }

    public void setHasHTTPImports(boolean hasHTTPImports) {
        this.hasHTTPImports = hasHTTPImports;
    }

    public boolean isHasLocalImports() {
        return hasLocalImports;
    }

    public void setHasLocalImports(boolean hasLocalImports) {
        this.hasLocalImports = hasLocalImports;
    }
}
