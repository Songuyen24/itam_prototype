/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ENABLE_DEMO_LOGIN?: string;
  readonly VITE_API_BASE_URL: string;
  readonly VITE_FRONTEND_PORT: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
