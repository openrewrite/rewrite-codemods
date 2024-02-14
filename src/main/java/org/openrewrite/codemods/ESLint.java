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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.scheduling.WorkingDirectoryExecutionContextView;
import org.openrewrite.text.PlainText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = true)
public class ESLint extends NodeBasedRecipe {

    private static final String ESLINT_DIR = ESLint.class.getName() + ".ESLINT_DIR";

    transient ESLintMessages messages = new ESLintMessages(this);

    @Option(displayName = "The lint target files",
            description = "The lint target files. This can contain any of file paths, directory paths, and glob patterns.",
            example = "lib/**/*.js",
            required = false)
    @Nullable
    List<String> patterns;

    @Option(displayName = "Parser to be used by ESLint",
            description = "Parser used by ESLint to parse the source files. Defaults to `@typescript-eslint/parser`. " +
                          "See [ESLint documentation](https://eslint.org/docs/latest/use/configure/parsers) for more details.",
            example = "esprima",
            required = false)
    @Nullable
    String parser;

    @Option(displayName = "List of parser options for ESLint",
            description = "A list of parser options for ESLint. The format is `key: value`. Defaults to " +
                          "`ecmaVersion: \"latest\", ecmaFeatures: { jsx: true }, sourceType: \"module\"`. See " +
                          "[ESLint documentation](https://eslint.org/docs/latest/use/configure/language-options#specifying-parser-options) for more details.",
            example = "ecmaVersion: 6, ecmaFeatures: { jsx: true }",
            required = false)
    @Nullable
    List<String> parserOptions;

    @Option(displayName = "Allow inline configuration for ESLint",
            description = "Whether inline config comments are allowed. Defaults to `false`. See " +
                          "[ESLint documentation](https://eslint.org/docs/latest/use/configure/rules#disabling-inline-comments) for more details.",
            example = "true",
            required = false)
    @Nullable
    Boolean allowInlineConfig;

    @Option(displayName = "List of `env` mappings for ESLint",
            description = "A list of `env` mappings for ESLint. The format is `key: value`.",
            example = "browser: true",
            required = false)
    @Nullable
    List<String> envs;

    @Option(displayName = "ESLint global variables",
            description = "Define global variables for rules that require knowledge of these.",
            example = "var1, var2: writable",
            required = false)
    @Nullable
    List<String> globals;

    @Option(displayName = "ESLint plugins",
            description = "A list of plugins for ESLint.",
            example = "@typescript-eslint, prettier",
            required = false)
    @Nullable
    List<String> plugins;

    @Option(displayName = "ESLint extends",
            description = "A list of extends for ESLint.",
            example = "eslint:recommended, prettier",
            required = false)
    @Nullable
    List<String> extend;

    @Option(displayName = "ESLint rules and rule configuration",
            description = "List of rules to be checked by ESLint. Optionally, the severity and other rule options can " +
                          "also be specified as e.g. `off`, `warn` or `[\"error\", \"always\"]`. " +
                          "The severity `off` is useful when the rule is declared by an extended " +
                          "[shareable config](https://eslint.org/docs/latest/extend/ways-to-extend#shareable-configs). " +
                          "For more information, see the [ESLint documentation](https://eslint.org/docs/latest/use/configure/rules)",
            example = "eqeqeq: warn, multiline-comment-style: [\"error\", \"starred-block\"], prettier/prettier",
            required = false)
    @Nullable
    List<String> rules;

    @Option(displayName = "Autofix",
            description = "Automatically fix violations when possible. Defaults to `true`.",
            example = "false",
            required = false)
    @Nullable
    Boolean fix;

    @Option(displayName = "Override config file",
            description = "Allows specifying the full ESLint configuration file contents as multiline JSON. " +
                          "See [ESLint documentation](https://eslint.org/docs/latest/use/configure/configuration-files) for more details.\n\n" +
                          "Note that this will override any other configuration options.",
            required = false)
    @Nullable
    String configFile;

    @Override
    public String getDisplayName() {
        return "Lint source code with ESLint";
    }

    @Override
    public String getDescription() {
        return "Run [ESLint](https://eslint.org/) across the code to fix common static analysis issues in the code.\n\n" +
               "This requires the code to have an existing ESLint configuration.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        Path path = RecipeResources.from(getClass()).extractResources("config", "eslint-config", ctx);
        ctx.putMessage(ESLINT_DIR, path);
        return super.getInitialValue(ctx);
    }

    @Override
    protected List<String> getNpmCommand(Accumulator acc, ExecutionContext ctx) {
        if ((plugins == null || plugins.isEmpty()) && (extend == null || extend.isEmpty()) && (rules == null || rules.isEmpty()) && configFile == null) {
            return emptyList();
        }

        List<String> command = new ArrayList<>();
        command.add("node");
        command.add(ctx.getMessage(ESLINT_DIR).toString() + "/eslint-driver.js");

        if (patterns != null) {
            patterns.forEach(p -> command.add("--patterns=" + p));
        }
        if (configFile != null) {
            try {
                Path directory = WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory();
                Path configFile = Files.write(Files.createTempFile(directory, "eslint-config", null), this.configFile.getBytes(StandardCharsets.UTF_8));
                command.add("--config-file=" + configFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (parser != null) {
                command.add("--parser=" + parser);
            }
            if (parserOptions != null) {
                parserOptions.forEach(p -> command.add("--parser-options=" + p));
            }
            if (allowInlineConfig != null) {
                command.add("--allow-inline-config=" + allowInlineConfig);
            }
            if (envs != null) {
                envs.forEach(e -> command.add("--env={" + e + "}"));
            }
            if (globals != null) {
                globals.forEach(g -> command.add("--globals={" + g + "}"));
            }
            if (plugins != null) {
                plugins.forEach(p -> command.add("--plugins=" + p));
            }
            if (extend != null) {
                extend.forEach(e -> command.add("--extends=" + e));
            }
            if (rules != null) {
                rules.forEach(r -> {
                    int colonIndex = r.indexOf(':');
                    if (colonIndex != -1) {
                        command.add("--rules={" + r + "}");
                    } else {
                        command.add("--rules={" + r + ": 2}");
                    }
                });
            }
            if (fix != null) {
                command.add("--fix=" + fix);
            }
        }
        return command;
    }

    @Override
    protected void processOutput(Path output, Accumulator acc, ExecutionContext ctx) {
        try {
            Map<Path, JsonNode> results = new HashMap<>();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode resultsNode = objectMapper.readTree(output.toFile());
            for (JsonNode resultNode : resultsNode.get("results")) {
                if (resultNode.get("errorCount").intValue() > 0 || resultNode.get("warningCount").intValue() > 0) {
                    results.put(Paths.get(resultNode.get("filePath").asText()), resultNode);
                }
            }
            acc.putData("results", results);
            acc.putData("metadata", resultsNode.get("metadata").get("rulesMeta"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected SourceFile createAfter(SourceFile before, Accumulator acc, ExecutionContext ctx) {
        Map<Path, JsonNode> results = acc.getData("results");
        if (results == null) {
            return super.createAfter(before, acc, ctx);
        }
        JsonNode resultNode = results.get(acc.resolvedPath(before));
        if (resultNode == null) {
            return super.createAfter(before, acc, ctx);
        }

        JsonNode metadata = acc.getData("metadata");
        String content = acc.content(before);
        List<PlainText.Snippet> snippets = new ArrayList<>();
        SourcePosition currentPosition = new SourcePosition(content, 1, 1, 0);
        PlainText.Snippet currentSnippet = new PlainText.Snippet(randomId(), Markers.EMPTY, "");
        JsonNode previousMessage = null;
        ArrayNode messagesNode = (ArrayNode) resultNode.get("messages");
        for (int i = 0; i < messagesNode.size(); i++) {
            JsonNode message = messagesNode.get(i);
            int line = message.get("line").asInt();
            int column = message.get("column").asInt();
            SourcePosition nextPosition = currentPosition.scanForwardTo(line, column);
            if (nextPosition.offset > currentPosition.offset) {
                if (previousMessage != null) {
                    SourcePosition endPosition = currentPosition.scanForwardTo(previousMessage.get("endLine").intValue(), previousMessage.get("endColumn").intValue());
                    if (endPosition.offset < nextPosition.offset) {
                        snippets.add(currentSnippet.withText(content.substring(currentPosition.offset, endPosition.offset)));
                        currentSnippet = new PlainText.Snippet(randomId(), Markers.EMPTY, "");
                        currentPosition = endPosition;
                    }
                }
                snippets.add(currentSnippet.withText(content.substring(currentPosition.offset, nextPosition.offset)));
                currentSnippet = new PlainText.Snippet(randomId(), Markers.EMPTY, "");
            }
            int severity = message.get("severity").asInt();
            String messageText = message.get("message").asText();
            String ruleId = message.get("ruleId").asText();
            JsonNode jsonNode = metadata != null ? metadata.get(ruleId) : null;
            String detail = jsonNode != null && jsonNode.has("docs") ?
                    jsonNode.get("docs").get("description").asText() + "\n\nRule: " + ruleId :
                    "Rule: " + ruleId;
            detail += ", Severity: " + (severity == 2 ? "ERROR" : "WARNING");
            Marker marker = new SearchResult(randomId(), messageText + "\n\n" + detail);
            messages.insertRow(
                    ctx, new ESLintMessages.Row(
                            before.getSourcePath().toString(),
                            ruleId,
                            ESLintMessages.Severity.of(severity),
                            message.has("fatal") && message.get("fatal").asBoolean(),
                            messageText,
                            line,
                            column
                    )
            );
            currentSnippet = currentSnippet.withMarkers(currentSnippet.getMarkers().add(marker));
            currentPosition = nextPosition;
            previousMessage = message.has("endLine") ? message : null;
        }
        if (previousMessage != null) {
            SourcePosition endPosition = currentPosition.scanForwardTo(previousMessage.get("endLine").intValue(), previousMessage.get("endColumn").intValue());
            if (endPosition.offset < content.length()) {
                snippets.add(currentSnippet.withText(content.substring(currentPosition.offset, endPosition.offset)));
                currentSnippet = new PlainText.Snippet(randomId(), Markers.EMPTY, "");
                currentPosition = endPosition;
            }
        }
        snippets.add(currentSnippet.withText(content.substring(currentPosition.offset)));

        return new PlainText(
                before.getId(),
                before.getSourcePath(),
                Boolean.TRUE.equals(fix) ? Markers.EMPTY : before.getMarkers(),
                before.getCharset() != null ? before.getCharset().name() : null,
                before.isCharsetBomMarked(),
                before.getFileAttributes(),
                null,
                "",
                snippets
        );
    }

    @Value
    private static class SourcePosition {
        String text;
        int line;
        int column;
        int offset;

        private SourcePosition scanForwardTo(int line, int column) {
            if (line == this.line && column == this.column) {
                return this;
            }
            int currentLine = this.line;
            int currentColumn = this.column;
            int currentOffset = this.offset;
            while (currentLine < line || (currentLine == line && currentColumn < column)) {
                if (text.charAt(currentOffset) == '\n') {
                    currentLine++;
                    currentColumn = 1;
                    currentOffset++;
                } else {
                    currentColumn++;
                    currentOffset++;
                }
            }
            return new SourcePosition(text, line, column, currentOffset);
        }
    }
}
