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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.sql.Timestamp;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "metrics")
@ApiModel(value = "Metrics", description = "Aggregated metrics associated with entry versions")
@Schema(name = "Metrics", description = "Aggregated metrics associated with entry versions")
public class Metrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the metrics in this webservice")
    @Schema(description = "Implementation specific ID for the metrics in this webservice")
    private long id;

    // This is a transient field that should not be saved to the database. Its purpose is for the user to specify an ID for the execution when submitting metrics,
    // which are then sent to S3.
    @NotEmpty
    @Transient
    @JsonProperty(required = true)
    @Schema(description = "User-provided ID of the execution. This ID is used to identify the execution when updating the execution", requiredMode = RequiredMode.REQUIRED)
    private String executionId;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "executionstatuscount", referencedColumnName = "id")
    @ApiModelProperty(value = "A count of the different execution statuses from the workflow executions", required = true)
    @Schema(description = "A count of the different execution statuses from the workflow executions", requiredMode = RequiredMode.REQUIRED)
    private ExecutionStatusCountMetric executionStatusCount;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "executiontime", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated execution time metrics in seconds")
    @Schema(description = "Aggregated execution time metrics in seconds")
    private ExecutionTimeStatisticMetric executionTime;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "memory", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated memory metrics in GB")
    @Schema(description = "Aggregated memory metrics in GB")
    private MemoryStatisticMetric memory;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cpu", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated CPU metrics")
    @Schema(description = "Aggregated CPU metrics")
    private CpuStatisticMetric cpu;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cost", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated cost metrics in USD")
    @Schema(description = "Aggregated cost metrics in USD")
    private CostStatisticMetric cost;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "validationstatus", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated validation status metrics")
    @Schema(description = "Aggregated validation status metrics")
    private ValidationStatusCountMetric validationStatus;

    @Deprecated(since = "1.15.0")
    @Transient // Don't persist to the database. This is meant to be used by platforms to submit additional aggregated metrics to Dockstore that aren't defined above.
    @JsonProperty
    @ApiModelProperty(value = "Additional aggregated metrics")
    @Schema(description = "Additional aggregated metrics", deprecated = true)
    private Map<String, Object> additionalAggregatedMetrics;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    @JsonIgnore
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @JsonIgnore
    private Timestamp dbUpdateDate;

    public Metrics() {
    }

    public Metrics(ExecutionStatusCountMetric executionStatusCount) {
        this.executionStatusCount = executionStatusCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @JsonIgnore // Avoid serializing this because the field is not stored in the DB and will always be null
    public String getExecutionId() {
        return executionId;
    }

    @JsonProperty
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public ExecutionStatusCountMetric getExecutionStatusCount() {
        return executionStatusCount;
    }

    public void setExecutionStatusCount(ExecutionStatusCountMetric executionStatusCount) {
        this.executionStatusCount = executionStatusCount;
    }

    public ExecutionTimeStatisticMetric getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(ExecutionTimeStatisticMetric executionTime) {
        this.executionTime = executionTime;
    }

    public MemoryStatisticMetric getMemory() {
        return memory;
    }

    public void setMemory(MemoryStatisticMetric memory) {
        this.memory = memory;
    }

    public CpuStatisticMetric getCpu() {
        return cpu;
    }

    public void setCpu(CpuStatisticMetric cpu) {
        this.cpu = cpu;
    }

    public CostStatisticMetric getCost() {
        return cost;
    }

    public void setCost(CostStatisticMetric cost) {
        this.cost = cost;
    }

    public ValidationStatusCountMetric getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatusCountMetric validationStatus) {
        this.validationStatus = validationStatus;
    }

    @Deprecated(since = "1.15.0")
    @JsonIgnore // Avoid serializing this because the field is not stored in the DB and will always be null
    public Map<String, Object> getAdditionalAggregatedMetrics() {
        return additionalAggregatedMetrics;
    }

    @Deprecated(since = "1.15.0")
    @JsonProperty
    public void setAdditionalAggregatedMetrics(Map<String, Object> additionalAggregatedMetrics) {
        this.additionalAggregatedMetrics = additionalAggregatedMetrics;
    }

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public void setDbCreateDate(Timestamp dbCreateDate) {
        this.dbCreateDate = dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }
}
