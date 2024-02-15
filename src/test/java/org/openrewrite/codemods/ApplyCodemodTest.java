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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.openrewrite.test.SourceSpecs.text;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
public class ApplyCodemodTest implements RewriteTest {

    @Test
    void formatStatement() {
        rewriteRun(
          spec -> spec.recipe(new ApplyCodemod("@kevinbarabash/codemods/transforms/array.js", null, null, null)),
          text(
            //language=js
            """
              _.tail(x)
              """,
            """
              x.slice(1)
              """,
            spec -> spec.path("src/Foo.js")
          )
        );
    }

    @Test
    void formatAngularStatement() {
        List<String> args = Arrays.asList("@angular/core@16", "@angular/cli@16");

        rewriteRun(
          spec -> spec.recipe(new ApplyCodemod(null, "@angular/cli/bin/ng.js", null, args)),
          text(
            //language=js
            """
              {
                           "$schema": "./node_modules/@angular/cli/lib/config/schema.json",
                           "version": 1,
                           "newProjectRoot": "projects",
                           "projects": {
                             "src": {
                               "projectType": "application",
                               "schematics": {},
                               "root": "",
                               "sourceRoot": "src",
                               "prefix": "app",
                               "architect": {
                                 "build": {
                                   "builder": "@angular-devkit/build-angular:application",
                                   "options": {
                                     "outputPath": "dist/src",
                                     "index": "src/index.html",
                                     "browser": "src/main.ts",
                                     "polyfills": [
                                       "zone.js"
                                     ],
                                     "tsConfig": "tsconfig.app.json",
                                     "assets": [
                                       "src/favicon.ico",
                                       "src/assets"
                                     ],
                                     "styles": [
                                       "src/styles.css"
                                     ],
                                     "scripts": []
                                   },
                                   "configurations": {
                                     "production": {
                                       "budgets": [
                                         {
                                           "type": "initial",
                                           "maximumWarning": "500kb",
                                           "maximumError": "1mb"
                                         },
                                         {
                                           "type": "anyComponentStyle",
                                           "maximumWarning": "2kb",
                                           "maximumError": "4kb"
                                         }
                                       ],
                                       "outputHashing": "all"
                                     },
                                     "development": {
                                       "optimization": false,
                                       "extractLicenses": false,
                                       "sourceMap": true
                                     }
                                   },
                                   "defaultConfiguration": "production"
                                 },
                                 "serve": {
                                   "builder": "@angular-devkit/build-angular:dev-server",
                                   "configurations": {
                                     "production": {
                                       "buildTarget": "src:build:production"
                                     },
                                     "development": {
                                       "buildTarget": "src:build:development"
                                     }
                                   },
                                   "defaultConfiguration": "development"
                                 },
                                 "extract-i18n": {
                                   "builder": "@angular-devkit/build-angular:extract-i18n",
                                   "options": {
                                     "buildTarget": "src:build"
                                   }
                                 },
                                 "test": {
                                   "builder": "@angular-devkit/build-angular:karma",
                                   "options": {
                                     "polyfills": [
                                       "zone.js",
                                       "zone.js/testing"
                                     ],
                                     "tsConfig": "tsconfig.spec.json",
                                     "assets": [
                                       "src/favicon.ico",
                                       "src/assets"
                                     ],
                                     "styles": [
                                       "src/styles.css"
                                     ],
                                     "scripts": []
                                   }
                                 }
                               }
                             }
                           }
                         }
                         
              """,
            """
              {
                            "$schema": "./node_modules/@angular/cli/lib/config/schema.json",
                            "version": 1,
                            "newProjectRoot": "projects",
                            "projects": {
                              "src": {
                                "projectType": "application",
                                "schematics": {},
                                "root": "",
                                "sourceRoot": "src",
                                "prefix": "app",
                                "architect": {
                                  "build": {
                                    "builder": "@angular-devkit/build-angular:application",
                                    "options": {
                                      "outputPath": "dist/src",
                                      "index": "src/index.html",
                                      "browser": "src/main.ts",
                                      "polyfills": [
                                        "zone.js"
                                      ],
                                      "tsConfig": "tsconfig.app.json",
                                      "assets": [
                                        "src/favicon.ico",
                                        "src/assets"
                                      ],
                                      "styles": [
                                        "src/styles.css"
                                      ],
                                      "scripts": []
                                    },
                                    "configurations": {
                                      "production": {
                                        "budgets": [
                                          {
                                            "type": "initial",
                                            "maximumWarning": "500kb",
                                            "maximumError": "1mb"
                                          },
                                          {
                                            "type": "anyComponentStyle",
                                            "maximumWarning": "2kb",
                                            "maximumError": "4kb"
                                          }
                                        ],
                                        "outputHashing": "all"
                                      },
                                      "development": {
                                        "optimization": false,
                                        "extractLicenses": false,
                                        "sourceMap": true
                                      }
                                    },
                                    "defaultConfiguration": "production"
                                  },
                                  "serve": {
                                    "builder": "@angular-devkit/build-angular:dev-server",
                                    "configurations": {
                                      "production": {
                                        "buildTarget": "src:build:production"
                                      },
                                      "development": {
                                        "buildTarget": "src:build:development"
                                      }
                                    },
                                    "defaultConfiguration": "development"
                                  },
                                  "extract-i18n": {
                                    "builder": "@angular-devkit/build-angular:extract-i18n",
                                    "options": {
                                      "buildTarget": "src:build"
                                    }
                                  },
                                  "test": {
                                    "builder": "@angular-devkit/build-angular:karma",
                                    "options": {
                                      "polyfills": [
                                        "zone.js",
                                        "zone.js/testing"
                                      ],
                                      "tsConfig": "tsconfig.spec.json",
                                      "assets": [
                                        "src/favicon.ico",
                                        "src/assets"
                                      ],
                                      "styles": [
                                        "src/styles.css"
                                      ],
                                      "scripts": []
                                    }
                                  }
                                }
                              }
                            }
                          }
                          
              """,
            spec -> spec.path("angular.json")
          )
        );
    }

    @Test
    void formatReactStatement() {
        rewriteRun(
          spec -> spec.recipe(new ApplyCodemod("react-declassify", "@codemod/cli/bin/codemod --plugin", "**/*.(j|t)sx", null)),
          text(
            //language=js
            """
              import React from "react";
                        
                        export class C extends React.Component {
                          render() {
                            const { text, color } = this.props;
                            return <button style={{ color }} onClick={() => this.onClick()}>{text}</button>;
                          }
                        
                          onClick() {
                            const { text, handleClick } = this.props;
                            alert(`${text} was clicked!`);
                            handleClick();
                          }
                        }
              """,
            """
              import React from "react";
                        
                        export const C = props => {
                          const {
                            text,
                            color,
                            handleClick
                          } = props;
                        
                          function onClick() {
                            alert(`${text} was clicked!`);
                            handleClick();
                          }
                        
                          return <button style={{ color }} onClick={() => onClick()}>{text}</button>;
                        };
              """,
            spec -> spec.path("src/Foo.js")
          )
        );
    }
}