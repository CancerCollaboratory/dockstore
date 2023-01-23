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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
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

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "executionstatuscount", referencedColumnName = "id")
    @ApiModelProperty(value = "A count of the different execution statuses from the workflow executions")
    @Schema(description = "A count of the different execution statuses from the workflow executions")
    private ExecutionStatusCountMetric executionStatusCount;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "executiontime", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated execution time metrics in ISO 8601 duration format")
    @Schema(description = "Aggregated execution time metrics in ISO 8601 duration format")
    private ExecutionTimeStatisticMetric executionTime;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "memory", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated memory metrics")
    @Schema(description = "Aggregated memory metrics")
    private MemoryStatisticMetric memory;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "cpu", referencedColumnName = "id")
    @ApiModelProperty(value = "Aggregated CPU metrics")
    @Schema(description = "Aggregated CPU metrics")
    private CpuStatisticMetric cpu;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;
    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Metrics(ExecutionStatusCountMetric executionStatusCount) {
        this.executionStatusCount = executionStatusCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
