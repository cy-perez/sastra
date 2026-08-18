import { defineConfig, devices } from '@playwright/test';

const PORT = 4173;
const BASE_URL = `http://localhost:${PORT}`;

/**
 * Las pruebas de extremo a extremo corren contra el servidor de renderizado
 * real, no contra el de desarrollo: lo que se quiere comprobar es justamente el
 * HTML que sale del servidor.
 *
 * Los navegadores no se descargan solos. La primera vez hace falta:
 *
 *   npx playwright install chromium
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env['CI']),
  retries: process.env['CI'] ? 2 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',

  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: {
    command: 'npm run build && node dist/sastra/server/server.mjs',
    url: BASE_URL,
    reuseExistingServer: !process.env['CI'],
    // El comando construye antes de servir, y una construccion en frio pasa de
    // los tres minutos. Con el margen anterior fallaba por tiempo justo cuando
    // mas falta hace: la primera ejecucion en una maquina limpia.
    timeout: 420_000,
    env: {
      PORT: String(PORT),
      NG_ALLOWED_HOSTS: 'localhost',
      // Ninguna prueba de esta carpeta llama a la API. La variable existe
      // porque el servidor no arranca sin ella, que es justo lo que se quiere.
      API_BASE_URL: process.env['API_BASE_URL'] ?? `${BASE_URL}/api/v1`,
      // El pie las muestra y ninguna es obligatoria, asi que sin declararlas
      // aqui la prueba no distinguiria "no se pintan" de "no habia que pintar".
      COMPANY_NAME: 'Sastra S.A.S.',
      COMPANY_TAX_ID: '000000000-0',
      COMPANY_ADDRESS: 'Medellin, Colombia',
      SUPPORT_EMAIL: 'hola@sastra.co',
    },
  },
});
