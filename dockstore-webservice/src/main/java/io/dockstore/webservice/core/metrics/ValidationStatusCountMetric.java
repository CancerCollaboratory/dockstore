/*
 * Copyright 2023 OICR and UCSC
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

package io.dockstore.webservice.core.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.webservice.core.metrics.ValidationExecution.ValidatorTool;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumMap;
import java.util.Map;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.MapKeyColumn;
import javax.persistence.MapKeyEnumerated;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotEmpty;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "validation_status")
@ApiModel(value = "ValidationStatusMetric", description = "Aggregated metrics about workflow validation statuses")
@Schema(name = "ValidationStatusMetric", description = "Aggregated metrics about workflow validation statuses")
@SuppressWarnings("checkstyle:magicnumber")
public class ValidationStatusCountMetric extends CountMetric<ValidatorTool, ValidationInfo> {

    @NotEmpty
    @JsonProperty("validatorToolToValidationInfo")
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "validator_tool_validation_info", joinColumns = @JoinColumn(name = "validationstatusid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "validationinfoid", referencedColumnName = "id", columnDefinition = "bigint"))
    @MapKeyColumn(name = "validatortool")
    @MapKeyEnumerated(EnumType.STRING)
    @BatchSize(size = 25)
    @ApiModelProperty(value = "A map containing key-value pairs indicating whether the validator tool successfully validated the workflow", required = true)
    @Schema(description = "A map containing key-value pairs indicating whether the validator tool successfully validated the workflow", required = true)
    private Map<ValidatorTool, ValidationInfo> count = new EnumMap<>(ValidatorTool.class);


    public ValidationStatusCountMetric() {
    }

    public ValidationStatusCountMetric(Map<ValidatorTool, ValidationInfo> validatorToolToIsValid) {
        this.count = validatorToolToIsValid;
    }

    @Override
    public Map<ValidatorTool, ValidationInfo> getCount() {
        return count;
    }

    public void setCount(Map<ValidatorTool, ValidationInfo> count) {
        this.count = count;
    }
}
