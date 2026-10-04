import { query } from '../database/db';
import { UserRole } from '../types';

const SENSITIVE_KEYS = new Set([
  'password',
  'password_hash',
  'token',
  'jwt',
  'secret',
  'ownersecret',
  'owner_secret',
  'owner_passcode',
  'passcode',
  'auth'
]);

export function isSensitiveKey(key: string): boolean {
  const k = key.toLowerCase();
  return SENSITIVE_KEYS.has(k) || k.includes('password') || k.includes('secret') || k.includes('token') || k === 'jwt';
}

export function sanitizeParameters(params: Record<string, any>): Record<string, any> {
  const sanitized: Record<string, any> = {};
  for (const [key, val] of Object.entries(params)) {
    if (isSensitiveKey(key)) {
      sanitized[key] = '[REDACTED]';
    } else if (val && typeof val === 'object' && !Array.isArray(val)) {
      sanitized[key] = sanitizeParameters(val);
    } else {
      sanitized[key] = val;
    }
  }
  return sanitized;
}

export async function logAdminAction(params: {
  adminUserId: string;
  adminUsername: string;
  adminRole: UserRole;
  action: string;
  targetIdentifier?: string;
  parameters: Record<string, any>;
  result: 'SUCCESS' | 'DENIED' | 'FAILED';
  ipAddress?: string;
}): Promise<void> {
  const sanitizedParams = sanitizeParameters(params.parameters);
  const sql = `
    INSERT INTO admin_audit_logs
    (admin_user_id, admin_username, admin_role, action, target_identifier, parameters, result, ip_address)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
  `;
  try {
    await query(sql, [
      params.adminUserId,
      params.adminUsername,
      params.adminRole,
      params.action,
      params.targetIdentifier || null,
      JSON.stringify(sanitizedParams),
      params.result,
      params.ipAddress || '127.0.0.1',
    ]);
  } catch (err) {
    console.error('[AUDIT FAILURE] Failed to write audit record:', (err as Error).message);
    throw new Error('Audit logging failed. Security policy aborted action.');
  }
}

export async function getAuditLogs(limit = 50, offset = 0) {
  return await query(`
    SELECT id, admin_user_id, admin_username, admin_role, action, target_identifier, parameters, result, ip_address, created_at
    FROM admin_audit_logs
    ORDER BY created_at DESC
    LIMIT $1 OFFSET $2
  `, [limit, offset]);
}
