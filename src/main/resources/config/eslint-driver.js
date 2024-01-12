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
const {ESLint} = require("eslint");
const optionator = require('optionator')({
    prepend: 'Usage: eslint-driver [options]',
    options: [{
        option: 'help',
        alias: 'h',
        type: 'Boolean',
        description: 'displays help',
    }, {
        option: 'patterns',
        type: '[String]',
        concatRepeatedArrays: true,
        description: 'The lint target files. This can contain any of file paths, directory paths, and glob patterns.',
    }, {
        option: 'env',
        type: 'Object',
        concatRepeatedArrays: true,
        description: 'Env setting for ESLint.',
    }, {
        option: 'plugins',
        type: '[String]',
        concatRepeatedArrays: true,
        description: 'ESLint plugins.',
    }, {
        option: 'extends',
        type: '[String]',
        concatRepeatedArrays: true,
        description: 'ESLint extends',
    }, {
        option: 'rules',
        type: 'Object',
        concatRepeatedArrays: true,
        description: 'ESLint rules.',
    }]
});

(async function main() {
    const options = optionator.parseArgv(process.argv);
    if (options.help) {
        console.log(optionator.generateHelp());
        return;
    }

    const patterns = options['patterns'] || ['**/*.js', '**/*.jsx'];
    const env = options['env'] || {};
    const plugins = options["plugins"] || ['@typescript-eslint'];
    const extend = options["extends"] || ['eslint:recommended', 'plugin:@typescript-eslint/recommended'];
    const rules = options["rules"] || {"eqeqeq": 2, "no-duplicate-imports": 2};
    if (typeof rules["prettier/prettier"] === "number" || typeof rules["prettier/prettier"] === "string") {
        rules["prettier/prettier"] = [ rules["prettier/prettier"], {}, { "usePrettierrc": false } ]
    }

    const eslint = new ESLint(
        {
            cwd: process.cwd(),
            errorOnUnmatchedPattern: false,
            allowInlineConfig: false,
            overrideConfig: {
                parser: "@typescript-eslint/parser",
                parserOptions: {
                    ecmaVersion: "latest",
                    ecmaFeatures: {
                        jsx: true
                    },
                    sourceType: "module"
                },
                env: env,
                plugins: plugins,
                extends: extend,
                rules: rules
            },
            // overrideConfigFile: "config/prettier.eslintrc.json",
            // resolvePluginsRelativeTo: "../codemods-npm",
            useEslintrc: false,
            fix: true,
            fixTypes: ["directive", "problem", "suggestion", "layout"]
        }
    );

    const results = await eslint.lintFiles(patterns);
    const formatter = await eslint.loadFormatter("json-with-metadata");
    const resultText = formatter.format(results);

    console.log(resultText);
    await ESLint.outputFixes(results);
})().catch((error) => {
    process.exitCode = 1;
    console.error(error);
})