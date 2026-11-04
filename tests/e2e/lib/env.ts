// Адреса берутся из переменных окружения; значения по умолчанию подходят для
// docker compose -f infra/compose.yaml -f infra/compose.mocks-ports.yaml
export const mocks = {
  crm: process.env.MOCK_CRM_URL ?? 'http://localhost:8081',
  billing: process.env.MOCK_BILLING_URL ?? 'http://localhost:8082',
  registry: process.env.MOCK_REGISTRY_URL ?? 'http://localhost:8083',
};

// Единственная точка входа сквозных тестов: Nginx
export const apiUrl = process.env.API_URL ?? 'http://localhost';
