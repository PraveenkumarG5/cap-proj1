/**
 * Transaction CSV generator for load testing the file-load job.
 * Usage examples (from backend/):
 *   node generate-transactions.js           # defaults to 1_000_000 rows
 *   node generate-transactions.js 2000000   # generate 2 million rows
 *   COUNT=500000 node generate-transactions.js
 */

const fs = require('fs');
const path = require('path');

const OUT_DIR = path.join(__dirname, 'inbound');
const OUT_FILE = path.join(OUT_DIR, 'transactions.csv');

// Allow configuration via CLI arg or env; default to 1,000,000 rows.
const countArg = parseInt(process.argv[2], 10);
const envCount = parseInt(process.env.COUNT, 10);
const TOTAL_ROWS = Number.isInteger(countArg)
  ? countArg
  : Number.isInteger(envCount)
    ? envCount
    : 1_000_000;

if (!fs.existsSync(OUT_DIR)) {
  fs.mkdirSync(OUT_DIR, { recursive: true });
}

const writeStream = fs.createWriteStream(OUT_FILE, { encoding: 'utf8' });
writeStream.write('txnId,accountNumber,amount,direction\n');

const directions = ['CREDIT', 'DEBIT'];
const ACCOUNTS = Array.from({ length: 50 }, (_, i) => `ACC-${1001 + i}`);
for (let i = 1; i <= TOTAL_ROWS; i++) {
  const accountNumber = ACCOUNTS[i % ACCOUNTS.length];
  const amount = (Math.random() * 200).toFixed(2);
  const direction = directions[i % 2];
  writeStream.write(`TXS-${i},${accountNumber},${amount},${direction}\n`);
}

writeStream.end(() => {
  console.log(`Generated ${TOTAL_ROWS} rows to ${OUT_FILE}`);
});


