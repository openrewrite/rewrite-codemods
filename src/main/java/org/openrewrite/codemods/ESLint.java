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
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = true)
public class ESLint extends AbstractNodeBasedRecipe {

    private static final String ESLINT_DIR = ESLint.class.getName() + ".ESLINT_DIR";

    transient ESLintMessages messages = new ESLintMessages(this);

    @Option(displayName = "The lint target files",
            description = "The lint target files. This can contain any of file paths, directory paths, and glob patterns.",
            example = "lib/**/*.js",
            required = false)
    @Nullable
    List<String> patterns;

    @Option(displayName = "List of `env` mappings for ESLint",
            description = "A list of `env` mappings for ESLint. The format is `key: value`.",
            example = "browser: true",
            required = false)
    @Nullable
    List<String> envs;

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

    @Option(displayName = "ESLint rules",
            description = "List of rules to be checked by ESLint. Optionally, the severity can also be specified as `off`, `warn` or `error` (defaults to `error`). " +
                          "The severity `off` is useful when the rule is configured by `extends`.",
            example = "eqeqeq: warn, prettier/prettier",
            required = false)
    @Nullable
    List<String> rules;

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
        if ((plugins == null || plugins.isEmpty()) && (extend == null || extend.isEmpty()) && (rules == null || rules.isEmpty())) {
            return emptyList();
        }

        List<String> command = new ArrayList<>();
        command.add("node");
        command.add(ctx.getMessage(ESLINT_DIR).toString() + "/eslint-driver.js");
        if (patterns != null) {
            patterns.forEach(p -> command.add("--patterns=" + p));
        }
        if (envs != null) {
            envs.forEach(e -> command.add("--env={" + e + "}"));
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
        for (JsonNode message : resultNode.get("messages")) {
            int line = message.get("line").asInt();
            int column = message.get("column").asInt();
            SourcePosition nextPosition = currentPosition.scanForwardTo(line, column);
            if (nextPosition.offset > currentPosition.offset) {
                snippets.add(currentSnippet.withText(content.substring(currentPosition.offset, nextPosition.offset)));
                currentSnippet = new PlainText.Snippet(randomId(), Markers.EMPTY, "");
            }
            int severity = message.get("severity").asInt();
            String messageText = message.get("message").asText();
            String ruleId = message.get("ruleId").asText();
            JsonNode jsonNode = metadata != null ? metadata.get(ruleId) : null;
            String detail = jsonNode != null ? jsonNode.get("docs").get("description").asText() + "\n\nRule: " + ruleId : "Rule: " + ruleId;
            Marker marker = new SearchResult(randomId(), (severity == 2 ? "ERROR: " : "WARNING: ") + messageText + "\n\n" + detail);
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
        }
        snippets.add(currentSnippet.withText(content.substring(currentPosition.offset)));

        return new PlainText(
                before.getId(),
                before.getSourcePath(),
                before.getMarkers(),
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
