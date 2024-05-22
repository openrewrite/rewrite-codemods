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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Value
@EqualsAndHashCode(callSuper = true)
public class Putout extends NodeBasedRecipe {

    private static final String PUTOUT_DIR = Putout.class.getName() + ".PUTOUT_DIR";

    @Override
    public String getDisplayName() {
        return "Run Putout";
    }

    @Override
    public String getDescription() {
        return "Run [Putout](https://github.com/coderaiser/putout) on your projects.";
    }

    @Option(displayName = "Config file",
            description = "A list of rules to enable",
            required = false)
    @Nullable
    Set<String> rules;

    @Override
    protected List<String> getNpmCommand(Accumulator acc, ExecutionContext ctx) {
        List<String> commands = new ArrayList<>();
        String executable = "${nodeModules}/.bin/putout";
        if (rules != null) {
            commands.add(executable + " ${repoDir} --disable-all || true"); // hacky because disable-all throws

            for (String rule : rules) {
                commands.add(executable + " ${repoDir} --enable " + rule);
            }
        }


        commands.add(executable + " ${repoDir}" + " --fix");
        return commands;
    }

    @Override
    protected void runNode(Accumulator acc, ExecutionContext ctx) {
        Path dir = acc.getDirectory();
        Path nodeModules = RecipeResources.from(getClass()).init(ctx);

        List<String> commandList = getNpmCommand(acc, ctx);
        if (commandList.isEmpty()) {
            return;
        }

        Map<String, String> env = getCommandEnvironment(acc, ctx);

        // Replace placeholders in commands
        List<String> processedCommands = new ArrayList<>();
        for (String cmd : commandList) {
            processedCommands.add(cmd
                    .replace("${nodeModules}", nodeModules.toString())
                    .replace("${repoDir}", ".")
                    .replace("${parser}", acc.parser()));
        }

        Path out = null, err = null;
        try {
            for (String cmd : processedCommands) {
                List<String> singleCommand = Arrays.asList("/bin/bash", "-c", cmd);

                ProcessBuilder builder = new ProcessBuilder(singleCommand);
                builder.directory(dir.toFile());
                builder.environment().put("NODE_PATH", nodeModules.toString());
                builder.environment().put("TERM", "dumb");
                env.forEach(builder.environment()::put);

                out = Files.createTempFile(WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory(), "node", null);
                err = Files.createTempFile(WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory(), "node", null);
                builder.redirectOutput(ProcessBuilder.Redirect.to(out.toFile()));
                builder.redirectError(ProcessBuilder.Redirect.to(err.toFile()));

                Process process = builder.start();
                process.waitFor(5, TimeUnit.MINUTES);
                if (process.exitValue() != 0) {
                    String error = "Command failed: " + cmd;
                    if (Files.exists(err)) {
                        error += "\n" + new String(Files.readAllBytes(err));
                    }
                    throw new RuntimeException(error);
                } else {
                    for (Map.Entry<Path, Long> entry : acc.beforeModificationTimestamps.entrySet()) {
                        Path path = entry.getKey();
                        if (!Files.exists(path) || Files.getLastModifiedTime(path).toMillis() > entry.getValue()) {
                            acc.modified(path);
                        }
                    }
                    processOutput(out, acc, ctx);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                //noinspection ResultOfMethodCallIgnored
                out.toFile().delete();
            }
            if (err != null) {
                //noinspection ResultOfMethodCallIgnored
                err.toFile().delete();
            }
        }
    }

    // TODO: support configuration files
//    @Override
//    protected Map<String, String> getCommandEnvironment(Accumulator acc, ExecutionContext ctx) {
//        if (configFile != null) {
//            try {
//                Path directory = WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory();
//                Path configFilePath = Files.createTempFile(directory, "putout-config", ".json");
//                Files.write(configFilePath, this.configFile.getBytes(StandardCharsets.UTF_8));
//                if (Files.exists(configFilePath)) {
//                    return new HashMap<String, String>() {{
//                        put("PUTOUT_CONFIG_FILE", configFilePath.toString());
//                    }};
//                } else {
//                    throw new RuntimeException("Configuration file not found: " + configFilePath.toString());
//                }
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        return new HashMap<>();
//    }

}