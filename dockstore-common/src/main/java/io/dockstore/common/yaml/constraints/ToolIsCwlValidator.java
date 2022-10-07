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

package io.dockstore.common.yaml.constraints;

import io.dockstore.common.yaml.YamlTool;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates that an apptool has the descriptor language CWL.
 */
public class ToolIsCwlValidator implements ConstraintValidator<ToolIsCwl, YamlTool> {
    @Override
    public void initialize(final ToolIsCwl constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(final YamlTool tool, final ConstraintValidatorContext context) {
        if (!"cwl".equalsIgnoreCase(tool.getSubclass())) {
            // create a violation that includes the 'subclass' property in the violation path.
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()).addPropertyNode("subclass").addConstraintViolation();
            return false;
        }
        return true;
    }
}
