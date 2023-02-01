/*
 *    Copyright 2020 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.common.yaml.constraints;

import io.dockstore.common.yaml.DockstoreYaml12AndUp;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates that a DockstoreYaml12+ instance has at least one entry (workflow, notebook, etc)
 */
public class HasEntryValidator extends BaseConstraintValidator<HasEntry, DockstoreYaml12AndUp> {

    @Override
    public boolean isValid(final DockstoreYaml12AndUp value, final ConstraintValidatorContext context) {
        if (value.getEntries().isEmpty()) {
            String message = String.format("must have at least one %s", String.join(", ", value.getEntryTerms()));
            addConstraintViolation(context, message);
            return false;
        }
        return true;
    }
}
