/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  /** Base URL for the markit API. Defaults to /api/v1 at runtime when unset. */
  readonly VITE_API_BASE?: string;
  /** Google Identity client id. When empty the Google button is hidden. */
  readonly VITE_GOOGLE_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
