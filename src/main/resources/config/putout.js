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

const fs = require('fs');
const path = require('path');

const configPath = path.join(process.cwd(), '.putout.json');
const printer = process.argv[2];

(async function main() {
  if (printer && fs.existsSync(configPath)) {
    console.log('Updating printer in .putout.json');
    try {
      // Read existing .putout.json into a JavaScript object
      const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));

      // Add or update the printer field
      config.printer = printer;

      // Write the updated configuration back to the file
      fs.writeFileSync(configPath, JSON.stringify(config, null, 2), 'utf8');
      console.log('Printer updated successfully!');
    } catch (err) {
      console.error('Error updating .putout.json:', err);
    }
  }
})().catch((error) => {
  process.exitCode = 1;
  console.error(error);
});
