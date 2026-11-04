import { defineConfig } from '@playwright/test';

// Три набора тестов:
//   fixtures  работает без стенда: проверяет сами тестовые данные (контрольные суммы ИНН и ОГРН)
//   mocks     требует поднятые моки (infra/compose.mocks-ports.yaml), проверяет контракты из integrations.md
//   api       сквозные сценарии через Nginx; пока заготовки (test.fixme), стенд с ядром и BFF ещё не собран
export default defineConfig({
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  projects: [
    { name: 'fixtures', testDir: './tests/fixtures' },
    { name: 'mocks', testDir: './tests/mocks' },
    { name: 'api', testDir: './tests/api' },
  ],
});
