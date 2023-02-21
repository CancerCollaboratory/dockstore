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

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.SequenceGenerator;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class CountMetric<T> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "countmetric_id_seq")
    @SequenceGenerator(name = "countmetric_id_seq", sequenceName = "countmetric_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the count metrics in this webservice")
    @Schema(description = "Implementation specific ID for the count metrics in this webservice")
    private long id;

    public abstract Map<T, Integer> getCount();

    public void addCount(T key, int count) {
        getCount().put(key, getCount().getOrDefault(key, 0) + count);
    }

    protected CountMetric() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}