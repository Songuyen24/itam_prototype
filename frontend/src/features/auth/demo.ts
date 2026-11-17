// Test account shortcuts are opt-in outside the local Vite development server.
export const demoLoginEnabled = import.meta.env.DEV || import.meta.env.VITE_ENABLE_DEMO_LOGIN === 'true';
export const demoPassword = import.meta.env.VITE_DEMO_PASSWORD ?? '';
export const demoRolePresets = [
  { email: 'demo.nv1@example.com', role: 'ADMIN', label: 'Administrator' },
  { email: 'demo.nv2@example.com', role: 'IT_STAFF', label: 'IT Staff' },
  { email: 'demo.nv3@example.com', role: 'PUR_STAFF', label: 'Purchasing Staff' },
  { email: 'demo.nv4@example.com', role: 'USER', label: 'End User' },
];
