// Все тексты интерфейса — в этом файле (nfr.md §10, ui-requirements.md §2 п. 8).
// Подписи кодов API — по ui-requirements.md §5, тексты ошибок — по §10.

import type { CompanyStatus, ContractType, RequestStatus, Role, TariffType } from '../api/types';

export const texts = {
  app: {
    name: 'B2B-Order-Flow',
  },

  common: {
    refresh: 'Обновить',
    retry: 'Повторить',
    cancel: 'Отмена',
    confirm: 'Подтвердить',
    save: 'Сохранить',
    close: 'Закрыть',
    copy: 'Скопировать',
    copied: 'Скопировано',
    loading: 'Загрузка…',
    required: 'Обязательное поле',
    correlationId: 'Код для обращения',
    backToHome: 'На главную',
    notImplemented: 'Экран появится в следующих задачах недели',
  },

  session: {
    expired: 'Сессия истекла, войдите снова',
    logout: 'Выйти',
    roleSwitcher: 'Активная роль',
    chooseRole: 'Выберите роль',
    companyPending: 'Компания ожидает подтверждения',
    companyRejected: 'Регистрация компании отклонена',
  },

  login: {
    title: 'Вход',
    login: 'Логин',
    password: 'Пароль',
    showPassword: 'Показать пароль',
    hidePassword: 'Скрыть пароль',
    submit: 'Войти',
    noAccount: 'Нет аккаунта?',
    register: 'Зарегистрироваться',
    unavailable: 'Сервис временно недоступен. Попробуйте войти через несколько секунд',
  },

  notFound: {
    title: 'Страница не найдена',
    text: 'Такой страницы нет или у вас нет к ней доступа.',
  },

  screens: {
    register: 'Регистрация',
    companyNew: 'Регистрация компании',
    company: 'Моя компания',
    adminCompanies: 'Подтверждение компаний',
    requests: 'Заявки',
    requestNew: 'Новая заявка',
    requestEdit: 'Черновик заявки',
    requestCard: 'Карточка заявки',
    managerQueue: 'Очередь менеджеров',
    queue: 'Очередь роли',
    myTasks: 'Мои задачи',
    notifications: 'Уведомления',
  },

  menu: {
    requests: 'Заявки',
    myRequests: 'Мои заявки',
    requestNew: 'Новая заявка',
    company: 'Моя компания',
    companyNew: 'Регистрация компании',
    managerQueue: 'Очередь менеджеров',
    myTasks: 'Мои задачи',
    queue: 'Очередь роли',
    stageQueues: 'Очереди этапов',
    adminCompanies: 'Подтверждение компаний',
    notifications: 'Уведомления',
  },

  roles: {
    CLIENT_OWNER: 'Владелец компании',
    CLIENT_ADMIN: 'Администратор компании',
    CLIENT_REQUESTER: 'Инициатор заявок',
    SALES_MANAGER: 'Менеджер по продажам',
    TECH_SPECIALIST: 'Технический специалист',
    LAWYER: 'Юрист',
    FINANCE_CONTROLLER: 'Финансовый контролёр',
    PROCUREMENT_SPECIALIST: 'Специалист по закупкам',
    B2B_HEAD: 'Руководитель B2B',
    ENGINEER: 'Инженер-монтажник',
    COORDINATOR: 'Координатор заявок',
    ADMIN: 'Системный администратор',
  } satisfies Record<Role, string>,

  requestStatus: {
    DRAFT: {
      label: 'Черновик',
      client: 'Заполните заявку и отправьте оператору',
      operator: '',
    },
    DATA_CHECK: {
      label: 'Проверка данных',
      client: 'Проверяем данные компании и назначаем менеджера',
      operator: 'Проверка во внешних системах; если менеджер не определён — заявка в очереди менеджеров',
    },
    WITH_MANAGER: {
      label: 'У менеджера',
      client: 'Менеджер готовит условия подключения',
      operator: 'Зафиксируйте коммерческие условия и отправьте в работу',
    },
    TECH_CHECK: {
      label: 'Проверка ТВ',
      client: 'Проверяем техническую возможность подключения',
      operator: 'Этап «Проверка ТВ» в очереди технических специалистов',
    },
    IN_APPROVAL: {
      label: 'На согласовании',
      client: 'Условия согласуются внутри оператора',
      operator: 'Идут согласования маршрута',
    },
    REWORK: {
      label: 'На доработке',
      client: 'Менеджер уточняет данные заявки',
      operator: 'Заявка возвращена менеджеру',
    },
    CONTRACT_SIGNING: {
      label: 'Подписание договора',
      client: 'Ознакомьтесь с условиями и подпишите договор',
      operator: 'Ожидается акцепт оферты или подписанный договор',
    },
    IN_EXECUTION: {
      label: 'В исполнении',
      client: 'Идут работы по подключению',
      operator: 'Стройка, монтаж, активация',
    },
    ACTIVATED: {
      label: 'Услуга активирована',
      client: 'Услуга работает; подпишите акт или сообщите о проблеме',
      operator: 'Ожидается акт или истечение срока приёмки',
    },
    CANCEL_REQUESTED: {
      label: 'Запрос на отмену',
      client: 'Рассматривается запрос на отмену',
      operator: 'Работы и SLA приостановлены',
    },
    CLOSED: {
      label: 'Закрыта',
      client: 'Услуга подключена, заявка закрыта',
      operator: '',
    },
    REJECTED: {
      label: 'Отклонена',
      client: 'Подключение невозможно; причина — в истории',
      operator: '',
    },
    CANCELLED: {
      label: 'Отменена',
      client: 'Заявка отменена',
      operator: '',
    },
  } satisfies Record<RequestStatus, { label: string; client: string; operator: string }>,

  integrationError: {
    operator: 'Ошибка проверки данных',
    client: 'Данные уточняются, это может занять больше времени',
  },

  companyStatus: {
    PENDING: 'На подтверждении',
    ACTIVE: 'Активна',
    REJECTED: 'Отклонена',
  } satisfies Record<CompanyStatus, string>,

  contractType: {
    OFFER: 'Оферта',
    STANDARD: 'Типовой договор',
    CLIENT_FORM: 'Договор по форме клиента',
  } satisfies Record<ContractType, string>,

  tariffType: {
    STANDARD: 'Типовой тариф',
    INDIVIDUAL: 'Индивидуальная цена',
  } satisfies Record<TariffType, string>,

  segment: {
    SMB: 'МСБ',
    LARGE: 'Крупный',
    STRATEGIC: 'Стратегический',
  } as Record<string, string>,

  commitmentLevel: ['До договора', 'Договор заключён', 'Работы начаты', 'Услуга активирована'],

  // Тексты ошибок по коду из ответа (api-contract.md §3.1). Ключ — code.
  errors: {
    VALIDATION_FAILED: 'Проверьте выделенные поля',
    MALFORMED_REQUEST: 'Не удалось обработать запрос',
    INVALID_CREDENTIALS: 'Неверный логин или пароль',
    UNAUTHORIZED: 'Сессия истекла, войдите снова',
    ACCESS_DENIED: 'Недостаточно прав для этого действия',
    MEMBERSHIP_REVOKED: 'Вы больше не состоите в компании. Обратитесь к её владельцу',
    NOT_FOUND: 'Заявка не найдена или у вас нет к ней доступа',
    ACTIVE_ROLE_REQUIRED: 'Выберите роль',
    ACTIVE_COMPANY_REQUIRED: 'Не удалось определить компанию. Обратитесь к администратору',
    COMPANY_NOT_CONFIRMED: 'Компания ещё не подтверждена администратором',
    COMPANY_INN_EXISTS: 'Компания с таким ИНН уже зарегистрирована. Попросите её владельца добавить вас по логину',
    COMPANY_INN_NOT_FOUND: 'ИНН не найден в справочнике клиентов. Проверьте ИНН',
    EXTERNAL_SYSTEM_UNAVAILABLE: 'Справочник клиентов временно недоступен. Повторите через минуту',
    ILLEGAL_TRANSITION: 'Действие уже недоступно: статус заявки изменился',
    VERSION_CONFLICT: 'Заявку изменили, пока вы её смотрели. Данные обновлены — проверьте и повторите действие',
    CONFLICT: 'Действие уже выполняется. Подождите и обновите страницу',
    IDEMPOTENCY_KEY_REUSED: 'Не удалось выполнить действие. Обновите страницу и повторите',
    STAGE_ALREADY_DECIDED: 'Этап уже закрыт другим пользователем',
    STAGE_NOT_IN_YOUR_QUEUE: 'Этот этап не в очереди вашей активной роли',
    SELF_APPROVAL_FORBIDDEN: 'Нельзя согласовать заявку, в которой вы менеджер',
    CANCEL_AFTER_ACTIVATION: 'После активации услуги отменить заявку нельзя',
    CORE_UNAVAILABLE: 'Сервис временно недоступен. Нажмите «Обновить» через несколько секунд',
    CORE_TIMEOUT: 'Сервис временно недоступен. Нажмите «Обновить» через несколько секунд',
    INTERNAL_ERROR: 'Непредвиденная ошибка',
    NETWORK_ERROR: 'Сервис временно недоступен. Нажмите «Обновить» через несколько секунд',
    TOO_MANY_REQUESTS: 'Слишком много запросов. Подождите минуту',
  } as Record<string, string>,

  // Для изменяющего действия при таймауте или обрыве связи исход неизвестен (ui-requirements.md §10)
  errorOutcomeUnknown:
    'Не удалось подтвердить, выполнено ли действие. Обновите страницу, чтобы увидеть актуальное состояние',
  errorFallback: 'Не удалось выполнить действие',

  warnings: {
    DUPLICATE_REQUEST_WARNING:
      'По этому адресу у компании уже есть незакрытая заявка. Если это ошибка, координатор отменит дубль',
    EXTERNAL_SYSTEM_UNAVAILABLE: 'Данные уточняются, это может занять больше времени',
  } as Record<string, string>,
};

export type Texts = typeof texts;
