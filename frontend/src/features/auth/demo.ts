// Test account shortcuts are opt-in outside the local Vite development server.
export const demoLoginEnabled = import.meta.env.DEV || import.meta.env.VITE_ENABLE_DEMO_LOGIN === 'true';
