export const INVENTORY_ROLES = ['ADMIN', 'IT_STAFF'];

export function canManageInventory(role?: string): boolean {
  return !!role && INVENTORY_ROLES.includes(role);
}

export function getHomePath(role?: string): string {
  if (canManageInventory(role)) return '/dashboard';
  if (role === 'USER') return '/my-assets';
  if (role === 'PUR_STAFF') return '/receivings';
  return '/login';
}
