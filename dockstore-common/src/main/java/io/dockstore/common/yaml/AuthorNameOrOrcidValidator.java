/*
 * Copyright 2022 OICR and UCSC
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
 */

package io.dockstore.common.yaml;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates that an author has a non-empty name or ORCID.
 */
public class AuthorNameOrOrcidValidator implements ConstraintValidator<AuthorNameOrOrcid, YamlAuthor> {
    @Override
    public void initialize(final AuthorNameOrOrcid constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(final YamlAuthor author, final ConstraintValidatorContext context) {
        return !StringUtils.isEmpty(author.getName()) || !StringUtils.isEmpty(author.getOrcid());
    }
}
