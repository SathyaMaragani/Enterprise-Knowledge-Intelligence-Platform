// Helpers over the profile from GET /api/auth/me. They decide what the UI offers;
// the backend enforces every rule again on each request.

const ROLE_LABELS = [
  ['ADMIN', 'Administrator'],
  ['MANAGER', 'Manager'],
  ['EMPLOYEE', 'Employee'],
];

export function hasRole(profile, role) {
  return Boolean(profile?.roles?.includes(role));
}

export function can(profile, permission) {
  return Boolean(profile?.permissions?.includes(permission));
}

/** The label for the most senior role held, or null when unknown. */
export function roleLabel(profile) {
  const match = ROLE_LABELS.find(([role]) => hasRole(profile, role));
  return match ? match[1] : null;
}
