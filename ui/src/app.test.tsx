import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiRequest } from './api/client';
import type { LoginResponse, UserView } from './api/types';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

const kim: UserView = {
  id: 'u-kim',
  login: 'i.kim',
  fullName: 'Игорь Ким',
  email: 'i.kim@example.test',
  companyRoles: [],
  operatorRoles: ['TECH_SPECIALIST', 'ENGINEER'],
  companies: [],
};

const loginResponse = (user: UserView): LoginResponse => ({ accessToken: 'jwt', expiresIn: 28800, user });

describe('каркас приложения', () => {
  it('без сессии отправляет на вход', async () => {
    mockApi({});
    const { router } = renderApp('/requests');
    expect(await screen.findByRole('heading', { name: texts.login.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('вход открывает главный экран роли, переключатель меняет роль и X-Active-Role', async () => {
    const fetchMock = mockApi({
      'POST /auth/login': () => jsonResponse(200, loginResponse(kim)),
      'GET /me': () => jsonResponse(200, kim),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/login');

    await user.type(await screen.findByTestId('login-login'), 'i.kim');
    await user.type(screen.getByTestId('login-password'), 'secret-password');
    await user.click(screen.getByTestId('login-submit'));

    expect(await screen.findByTestId('placeholder-ui-12')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/my-tasks');
    expect(sessionStore.getActiveRole()).toBe('TECH_SPECIALIST');

    // Переключаем роль: Select MUI открывается по mouseDown
    await user.click(screen.getByRole('combobox', { name: texts.session.roleSwitcher }));
    await user.click(await screen.findByRole('option', { name: texts.roles.ENGINEER }));

    expect(sessionStore.getActiveRole()).toBe('ENGINEER');
    expect(router.state.location.pathname).toBe('/my-tasks');

    // Слой запросов берёт токен и роль из сессии: следующий запрос уходит с новой ролью
    fetchMock.mockClear();
    await apiRequest('GET', '/me');
    const headers = new Headers(fetchMock.mock.calls[0]![1]?.headers);
    expect(headers.get('X-Active-Role')).toBe('ENGINEER');
    expect(headers.get('Authorization')).toBe('Bearer jwt');

    await user.click(screen.getByRole('link', { name: texts.menu.requests }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/requests'));
  });

  it('неверный пароль показывает текст ошибки по коду', async () => {
    mockApi({ 'POST /auth/login': () => jsonResponse(401, { code: 'INVALID_CREDENTIALS', retryable: false }) });
    const user = userEvent.setup();
    renderApp('/login');

    await user.type(await screen.findByTestId('login-login'), 'nobody');
    await user.type(screen.getByTestId('login-password'), 'wrong-password');
    await user.click(screen.getByTestId('login-submit'));

    expect(await screen.findByTestId('login-error')).toHaveTextContent(texts.errors.INVALID_CREDENTIALS!);
  });

  it('истёкший токен (401 на /me) возвращает на вход с сообщением', async () => {
    sessionStore.setToken('expired-jwt', 28800);
    mockApi({ 'GET /me': () => jsonResponse(401, { code: 'UNAUTHORIZED', retryable: false }) });
    const { router } = renderApp('/requests');

    expect(await screen.findByTestId('login-expired')).toHaveTextContent(texts.session.expired);
    expect(router.state.location.pathname).toBe('/login');
    expect(sessionStore.getToken()).toBeNull();
  });

  it('экран чужой роли открывает главный экран активной роли', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('ENGINEER');
    mockApi({ 'GET /me': () => jsonResponse(200, kim) });
    const { router } = renderApp('/admin/companies');

    await waitFor(() => expect(router.state.location.pathname).toBe('/my-tasks'));
  });

  it('сбой связи при загрузке профиля показывает ошибку с кнопкой «Обновить»', async () => {
    sessionStore.setToken('jwt', 28800);
    mockApi({ 'GET /me': () => jsonResponse(503, { code: 'CORE_UNAVAILABLE', retryable: true }) });
    renderApp('/requests');

    expect(await screen.findByTestId('state-error', {}, { timeout: 3000 })).toHaveTextContent(
      texts.errors.CORE_UNAVAILABLE!,
    );
    expect(screen.getByRole('button', { name: texts.common.refresh })).toBeInTheDocument();
  });
});
