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
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Schema(name = "StatisticMetric", description = "Describes a metric with statistics")
public abstract class StatisticMetric extends Metric {

    @Column(nullable = false)
    @NotNull
    @ApiModelProperty(value = "The minimum value from the data points", required = true)
    @Schema(description = "The minimum value from the data points", requiredMode = RequiredMode.REQUIRED)
    private double minimum;

    @Column(nullable = false)
    @NotNull
    @ApiModelProperty(value = "The maximum value from the data points", required = true)
    @Schema(description = "The maximum value from the data points", requiredMode = RequiredMode.REQUIRED)
    private double maximum;

    @Column(nullable = false)
    @NotNull
    @ApiModelProperty(value = "The average value from the data points", required = true)
    @Schema(description = "The average value from the data points", requiredMode = RequiredMode.REQUIRED)
    private double average;

    @Column(nullable = false)
    @NotNull
    @ApiModelProperty(value = "The number of data points used to calculate the average", required = true)
    @Schema(description = "The number of data points used to calculate the average", requiredMode = RequiredMode.REQUIRED)
    private int numberOfDataPointsForAverage;

    @Column
    @ApiModelProperty(value = "The unit of the data points")
    @Schema(description = "The unit of the data points")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String unit;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    @JsonIgnore
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @JsonIgnore
    private Timestamp dbUpdateDate;

    protected StatisticMetric() {
    }

    protected StatisticMetric(double minimum, double maximum, double average, int numberOfDataPointsForAverage) {
        this(minimum, maximum, average, numberOfDataPointsForAverage, null);
    }

    protected StatisticMetric(double minimum, double maximum, double average, int numberOfDataPointsForAverage, String unit) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.average = average;
        this.numberOfDataPointsForAverage = numberOfDataPointsForAverage;
        this.unit = unit;
    }

    public double getMinimum() {
        return minimum;
    }

    public void setMinimum(double minimum) {
        this.minimum = minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    public void setMaximum(double maximum) {
        this.maximum = maximum;
    }

    public double getAverage() {
        return average;
    }

    public void setAverage(double average) {
        this.average = average;
    }

    public int getNumberOfDataPointsForAverage() {
        return numberOfDataPointsForAverage;
    }

    public void setNumberOfDataPointsForAverage(int numberOfDataPointsForAverage) {
        this.numberOfDataPointsForAverage = numberOfDataPointsForAverage;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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
