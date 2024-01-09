/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.codemods;

import org.openrewrite.ExecutionContext;

import java.util.Arrays;
import java.util.List;

public class ESLint extends AbstractNpmBasedRecipe {

    @Override
    public String getDisplayName() {
        return "Run `eslint --fix`";
    }

    @Override
    public String getDescription() {
        return "Run [ESLint](https://eslint.org/) across the code to fix common static analysis issues in the code.\n\n" +
                "This requires the code to have an existing ESLint configuration.";
    }

    @Override
    protected List<String> getNpmCommand(Accumulator acc, ExecutionContext ctx) {
        return Arrays.asList("${nodeModules}/.bin/eslint", "--fix", "**/*.js");
    }
}
