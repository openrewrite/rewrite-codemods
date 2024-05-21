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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.scheduling.WorkingDirectoryExecutionContextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value
@EqualsAndHashCode(callSuper = true)
public class Putout extends NodeBasedRecipe {

    @Override
    public String getDisplayName() {
        return "Run Putout";
    }

    @Override
    public String getDescription() {
        return "Run [Putout](https://github.com/coderaiser/putout) on your projects.";
    }

    @Option(displayName = "Config file",
            description = "A [Putout configuration file](https://github.com/coderaiser/putout?tab=readme-ov-file#-configuration) contents as multiline JSON.",
            required = false)
    @Nullable
    String configFile;

    @Override
    protected List<String> getNpmCommand(Accumulator acc, ExecutionContext ctx) {
        List<String> command = new ArrayList<>();
        command.add("${nodeModules}/.bin/putout ${repoDir}");
        command.add("--fix");
        return command;
    }

    @Override
    protected Map<String, String> getCommandEnvironment(Accumulator acc, ExecutionContext ctx) {
        if (configFile != null) {
            try {
                Path directory = WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory();
                Path configFile = Files.write(Files.createTempFile(directory, "putout-config", null), this.configFile.getBytes(StandardCharsets.UTF_8));
                return new HashMap<String, String>() {{
                    put("PUTOUT_CONFIG_FILE", configFile.toString());
                }};
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new HashMap<>();
    }
}