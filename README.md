![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)
## Apply JavaScript / TypeScript codemods

This repository provides JavaScript / TypeScript codemods wrapped up into OpenRewrite recipes, so that they can be applied from the command line or using the [platform](https://app.moderne.io/).
Currently, the support is limited to [jscodeshift](https://github.com/facebook/jscodeshift)-based codemods.

## Implementation Notes

As existing codemods have not been designed to operate on OpenRewrite LSTs, the recipes for codemods operate in a very special way and also have certain limitations.
All recipes based on [ApplyCodemod](src/main/java/org/openrewrite/codemods/ApplyCodemod.java) are scanning recipes and operate as follows:
1. In the scanning phase (when all source files are available with the original content) all sources are serialized to the recipe's working directory.
Thus, this basically recreates the original Git repository in the local file system.
2. In the generate phase the recipe also extracts the [packaged Node modules](src/main/resources/codemods) into another directory in the local file system and then finally uses the `node` executable to apply the codemod to the repo as created in the scanning phase.
3. Finally, in the edit phase, the visitor checks if the current source file was modified by the codemod and if so returns an updated source file.
Note that the source files will then always be `PlainText` sources.

If there are multiple codemod recipes contained in the current recipe run (all based on `ApplyCodemod`) then the recipes are "chained" so that the input (i.e. source files) to a codemod is the result of the previous codemod. To achieve this with separation the generate phase copies the entire directory with the source files before running the codemod.

The limitations imposed by this implementation are due to the fact that the actual modifications are already performed in the generate phase. So as soon as codemod recipes are combined with other recipes in the same recipe run, this breaks down: The codemod recipes won't "see" the changes performed by other recipes (which modify sources in the edit phase) and vice versa.

> [!IMPORTANT]
> `ApplyCodemod`-based recipes can currently not be combined with other recipes (not based on `ApplyCodemod`) in the same recipe run.
