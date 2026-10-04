import { Pool, PoolClient, PoolConfig } from 'pg';
import dotenv from 'dotenv';

dotenv.config();

export function getDatabaseConfig(): PoolConfig {
  if (process.env.DATABASE_URL) {
    return {
      connectionString: process.env.DATABASE_URL,
      max: 20,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 5000,
    };
  }

  if (process.env.DB_HOST && process.env.DB_USER && process.env.DB_NAME && process.env.DB_PASSWORD) {
    return {
      host: process.env.DB_HOST,
      port: parseInt(process.env.DB_PORT || '5432', 10),
      database: process.env.DB_NAME,
      user: process.env.DB_USER,
      password: process.env.DB_PASSWORD,
      max: 20,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 5000,
    };
  }

  throw new Error('FATAL: Database configuration missing. DATABASE_URL must be provided. Insecure default credential fallbacks are disallowed.');
}

let poolInstance: Pool | null = null;

export function getPool(): Pool {
  if (!poolInstance) {
    poolInstance = new Pool(getDatabaseConfig());
  }
  return poolInstance;
}

export const pool = {
  query: (text: string, params?: any[]) => getPool().query(text, params),
  connect: () => getPool().connect(),
  end: () => poolInstance ? poolInstance.end() : Promise.resolve(),
} as unknown as Pool;

export async function query<T = any>(sql: string, params: any[] = []): Promise<T[]> {
  try {
    const res = await getPool().query(sql, params);
    return res.rows;
  } catch (err) {
    console.error(`[Database Error] SQL: ${sql.slice(0, 100)} | Error:`, (err as Error).message);
    throw err;
  }
}

export async function withTransaction<T>(callback: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    const result = await callback(client);
    await client.query('COMMIT');
    return result;
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}

export async function checkDatabaseHealth(): Promise<boolean> {
  try {
    const res = await getPool().query('SELECT 1 as alive');
    return res.rows[0]?.alive === 1;
  } catch {
    return false;
  }
}

export async function assertDatabaseConnection(): Promise<void> {
  // Validate configuration presence first
  getDatabaseConfig();
  const isHealthy = await checkDatabaseHealth();
  if (!isHealthy) {
    throw new Error('FATAL: Database connection failed. Refusing server startup.');
  }
}
