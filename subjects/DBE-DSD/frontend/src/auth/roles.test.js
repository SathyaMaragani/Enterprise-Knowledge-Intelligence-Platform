import { describe, expect, it } from 'vitest';
import { can, hasRole, roleLabel } from './roles.js';

const admin = { roles: ['ADMIN'], permissions: ['DOCUMENT_CREATE', 'USER_MANAGE'] };
const manager = { roles: ['MANAGER'], permissions: ['DOCUMENT_CREATE', 'DOCUMENT_READ'] };
const employee = { roles: ['EMPLOYEE'], permissions: ['DOCUMENT_READ'] };

describe('roles', () => {
  it('labels the most senior role held', () => {
    expect(roleLabel(admin)).toBe('Administrator');
    expect(roleLabel(manager)).toBe('Manager');
    expect(roleLabel(employee)).toBe('Employee');
    expect(roleLabel({ roles: ['EMPLOYEE', 'MANAGER'] })).toBe('Manager');
  });

  it('knows nothing about an unknown or missing profile', () => {
    expect(roleLabel(null)).toBeNull();
    expect(roleLabel({ roles: ['AUDITOR'] })).toBeNull();
    expect(hasRole(null, 'ADMIN')).toBe(false);
    expect(can(undefined, 'DOCUMENT_READ')).toBe(false);
  });

  it('checks roles and permissions exactly', () => {
    expect(hasRole(admin, 'ADMIN')).toBe(true);
    expect(hasRole(manager, 'ADMIN')).toBe(false);
    expect(can(manager, 'DOCUMENT_CREATE')).toBe(true);
    expect(can(employee, 'DOCUMENT_CREATE')).toBe(false);
  });
});
