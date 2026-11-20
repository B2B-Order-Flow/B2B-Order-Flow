import { defineConfig } from '@playwright/test';

// Три набора тестов:
//   fixtures  работает без стенда: проверяет сами тестовые данные (контрольные суммы ИНН и ОГРН)
//   mocks     требует поднятые моки (infra/compose.mocks-ports.yaml), проверяет контракты из integrations.md
//   api       сквозные сценарии через Nginx на стенде DEMO (нужен DEMO_PASSWORD); SC-01 готов, остальные пока test.fixme
export default defineConfig({
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  projects: [
    { name: 'fixtures', testDir: './tests/fixtures' },
    { name: 'mocks', testDir: './tests/mocks' },
    // Сценарий делает около 25 запросов, а submit ждёт опроса внешних систем: запас к стандартным 30 с
    { name: 'api', testDir: './tests/api', timeout: 60_000 },
  ],
});
