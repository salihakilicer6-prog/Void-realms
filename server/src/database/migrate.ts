import fs from 'fs';
import path from 'path';
import { pool, withTransaction, query } from './db';

export async function runMigrations(migrationsDir?: string): Promise<string[]> {
  const dir = migrationsDir || path.resolve(__dirname, '../../../database/migrations');
  if (!fs.existsSync(dir)) {
    throw new Error(`Migrations directory not found: ${dir}`);
  }

  // Ensure migrations tracking table exists
  await query(`
    CREATE TABLE IF NOT EXISTS migrations_applied (
      id SERIAL PRIMARY KEY,
      filename VARCHAR(255) UNIQUE NOT NULL,
      checksum VARCHAR(64),
      applied_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );
  `);

  const appliedRows = await query<{ filename: string }>('SELECT filename FROM migrations_applied');
  const appliedSet = new Set(appliedRows.map(r => r.filename));

  const files = fs.readdirSync(dir)
    .filter(f => f.endsWith('.sql'))
    .sort();

  const newlyApplied: string[] = [];

  for (const file of files) {
    if (appliedSet.has(file)) {
      continue;
    }

    const filePath = path.join(dir, file);
    const sql = fs.readFileSync(filePath, 'utf-8');

    await withTransaction(async (client) => {
      console.log(`[MIGRATION] Applying: ${file}...`);
      await client.query(sql);
      await client.query(
        'INSERT INTO migrations_applied (filename) VALUES ($1)',
        [file]
      );
    });

    newlyApplied.push(file);
    console.log(`[MIGRATION] Successfully applied: ${file}`);
  }

  return newlyApplied;
}

if (require.main === module) {
  runMigrations()
    .then((applied) => {
      console.log(`[MIGRATION] Finished. Applied ${applied.length} new migrations.`);
      process.exit(0);
    })
    .catch((err) => {
      console.error('[MIGRATION] Fatal migration error:', err);
      process.exit(1);
    });
}
