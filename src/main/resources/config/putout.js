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
