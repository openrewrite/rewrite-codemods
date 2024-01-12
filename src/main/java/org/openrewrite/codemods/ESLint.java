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
import lombok.AllArgsConstructor;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.Markup;
import org.openrewrite.text.PlainText;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.openrewrite.Tree.randomId;

@AllArgsConstructor
public class ESLint extends AbstractNpmBasedRecipe {

    private static final String ESLINT_DIR = ESLint.class.getName() + ".ESLINT_DIR";

    @Option(displayName = "The lint target files",
            description = "The lint target files. This can contain any of file paths, directory paths, and glob patterns.\n\n" +
                          "Defaults to `**/*.js, **/*.jsx`",
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
        Path path = NodeModules.extractResources("config", "eslint-config", ctx);
        ctx.putMessage(ESLINT_DIR, path);
        return super.getInitialValue(ctx);
    }

    @Override
    protected List<String> getNpmCommand(Accumulator acc, ExecutionContext ctx) {
        List<String> command = new ArrayList<>();
        command.add("node");
        command.add(ctx.getMessage(ESLINT_DIR).toString() + "/eslint-driver.js");
        if (patterns != null) {
            patterns.forEach(p -> command.add("--patterns=" + p));
        } else {
            command.add("--patterns=**/*.js");
            command.add("--patterns=**/*.jsx");
        }
        if (envs != null) {
            envs.forEach(e -> command.add("--env={" + e + "}"));
        }
        return command;
    }

    @Override
    protected void processOutput(Path output, Accumulator acc, ExecutionContext ctx) {
        try {
            Map<Path, JsonNode> results = new HashMap<>();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode resultsNode = objectMapper.readTree(output.toFile());
            for (JsonNode resultNode : resultsNode) {
                if (resultNode.get("errorCount").intValue() > 0 || resultNode.get("warningCount").intValue() > 0) {
                    results.put(Paths.get(resultNode.get("filePath").asText()), resultNode);
                }
            }
            acc.putData("results", results);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected SourceFile createAfter(SourceFile before, Accumulator acc) {
        Map<Path, JsonNode> results = acc.getData("results");
        JsonNode resultNode = results.get(acc.resolvedPath(before));
        if (resultNode == null) {
            return super.createAfter(before, acc);
        }

        String content = acc.content(before);
        List<PlainText.Snippet> snippets = new ArrayList<>();
        SourcePosition currentPosition = new SourcePosition(content, 1, 1, 0);
        PlainText.Snippet currentSnippet = new PlainText.Snippet(randomId(), Markers.EMPTY, "");
        for (JsonNode message : resultNode.get("messages")) {
            SourcePosition nextPosition = currentPosition.scanForwardTo(message.get("line").asInt(), message.get("column").asInt());
            if (nextPosition.offset > currentPosition.offset) {
                snippets.add(currentSnippet.withText(content.substring(currentPosition.offset, nextPosition.offset)));
                currentSnippet = new PlainText.Snippet(randomId(), Markers.EMPTY, "");
            }
            int severity = message.get("severity").asInt();
            String messageText = message.get("message").asText();
            Marker marker = severity == 2 ? new Markup.Error(randomId(), messageText, "") : new Markup.Warn(randomId(), messageText, "");
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
