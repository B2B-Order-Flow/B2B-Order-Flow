import type { Role } from '../api/types';

// Сессия хранится в sessionStorage вкладки (ui-requirements.md §3.5): обновление страницы не разлогинивает,
// закрытие вкладки завершает сессию. Хранилище может быть недоступно (приватный режим, блокировка) —
// тогда сессия живёт только в памяти.

const TOKEN_KEY = 'b2b.session.token';
const EXPIRES_KEY = 'b2b.session.expiresAt';
const ROLE_KEY = 'b2b.session.activeRole';
const EXPIRED_NOTICE_KEY = 'b2b.session.expiredNotice';

const memory = new Map<string, string>();

function read(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return memory.get(key) ?? null;
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) window.sessionStorage.removeItem(key);
    else window.sessionStorage.setItem(key, value);
  } catch {
    if (value === null) memory.delete(key);
    else memory.set(key, value);
  }
}

export const sessionStore = {
  getToken(now = Date.now()): string | null {
    const token = read(TOKEN_KEY);
    const expiresAt = Number(read(EXPIRES_KEY));
    if (!token) return null;
    if (expiresAt && expiresAt <= now) {
      this.clear();
      return null;
    }
    return token;
  },

  setToken(token: string, expiresInSeconds: number, now = Date.now()): void {
    write(TOKEN_KEY, token);
    write(EXPIRES_KEY, String(now + expiresInSeconds * 1000));
  },

  getActiveRole(): Role | null {
    return read(ROLE_KEY) as Role | null;
  },

  setActiveRole(role: Role | null): void {
    write(ROLE_KEY, role);
  },

  clear(): void {
    write(TOKEN_KEY, null);
    write(EXPIRES_KEY, null);
    write(ROLE_KEY, null);
  },

  /** Флаг «сессия истекла»: показывается один раз на экране входа. */
  markExpired(): void {
    write(EXPIRED_NOTICE_KEY, '1');
  },

  takeExpiredNotice(): boolean {
    const value = read(EXPIRED_NOTICE_KEY) === '1';
    write(EXPIRED_NOTICE_KEY, null);
    return value;
  },
};
