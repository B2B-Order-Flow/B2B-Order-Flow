// Тестовые организации из docs/system/integrations.md, раздел 5.
// При изменении таблицы в документе правится этот файл и стабы в mocks/.

export type CrmMode =
  | 'ok' // обычный ответ 200
  | 'absent' // 404 NOT_FOUND
  | 'timeout' // ответ через 30 с, дольше таймаута ядра (2 с)
  | 'slow' // ответ через 1,5 с, в пределах таймаута
  | 'flaky' // 503, 503, затем 200 (сценарий WireMock)
  | 'down'; // всегда 503

export interface Org {
  name: string;
  inn: string;
  kpp: string | null;
  ogrn: string | null;
  segment: 'SMB' | 'LARGE' | 'STRATEGIC' | null;
  manager: string | null;
  debt: string | null; // null: в биллинге нет лицевого счёта
  gov: boolean;
  crm: CrmMode;
  inRegistry: boolean;
  usedIn: string;
}

export const orgs: Org[] = [
  {
    name: 'ИП Соколов А. В.',
    inn: '770112345127',
    kpp: null,
    ogrn: '304777701123450',
    segment: 'SMB',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: false,
    crm: 'ok',
    inRegistry: true,
    usedIn: 'SC-01',
  },
  {
    name: 'ООО «Логистик Плюс»',
    inn: '7725012341',
    kpp: '772501001',
    ogrn: '1027725012347',
    segment: 'LARGE',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: false,
    crm: 'ok',
    inRegistry: true,
    usedIn: 'SC-02',
  },
  {
    name: 'АО «Агрохолдинг Заря»',
    inn: '5050123455',
    kpp: '505001001',
    ogrn: '1025050123450',
    segment: 'STRATEGIC',
    manager: null,
    debt: '0.00',
    gov: false,
    crm: 'ok',
    inRegistry: true,
    usedIn: 'SC-03, EC-19',
  },
  {
    name: 'ООО «Вектор»',
    inn: '7811001128',
    kpp: '781101001',
    ogrn: '1027811001129',
    segment: 'SMB',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: false,
    crm: 'ok',
    inRegistry: true,
    usedIn: 'SC-00',
  },
  {
    name: 'ООО «Северторг»',
    inn: '7844002230',
    kpp: '784401001',
    ogrn: '1027844002230',
    segment: 'SMB',
    manager: 'a.smirnova',
    debt: '45000.00',
    gov: false,
    crm: 'ok',
    inRegistry: true,
    usedIn: 'EC-03',
  },
  {
    name: 'ГБУ «Городская больница № 7»',
    inn: '7719003349',
    kpp: '771901001',
    ogrn: '1027719003344',
    segment: 'LARGE',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: true,
    crm: 'ok',
    inRegistry: true,
    usedIn: 'R-02',
  },
  {
    name: 'ООО «Таймаут»',
    inn: '9999005565',
    kpp: '999901001',
    ogrn: '1029999005563',
    segment: 'SMB',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: false,
    crm: 'timeout',
    inRegistry: true,
    usedIn: 'EC-10',
  },
  {
    name: 'ООО «Медленный»',
    inn: '9999000052',
    kpp: '999901001',
    ogrn: '1029999000052',
    segment: 'SMB',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: false,
    crm: 'slow',
    inRegistry: true,
    usedIn: 'EC-10',
  },
  {
    name: 'ООО «Шаткий»',
    inn: '9999000013',
    kpp: '999901001',
    ogrn: '1029999000019',
    segment: 'SMB',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: false,
    crm: 'flaky',
    inRegistry: true,
    usedIn: 'EC-10',
  },
  {
    name: 'ООО «Глухой»',
    inn: '9999000020',
    kpp: '999901001',
    ogrn: '1029999000020',
    segment: 'SMB',
    manager: 'a.smirnova',
    debt: '0.00',
    gov: false,
    crm: 'down',
    inRegistry: true,
    usedIn: 'EC-10',
  },
  // Нет ни в одной системе: ИНН корректный, но организации нигде нет (SC-00 A1)
  {
    name: 'ООО «Фантом»',
    inn: '9977004455',
    kpp: null,
    ogrn: null,
    segment: null,
    manager: null,
    debt: null,
    gov: false,
    crm: 'absent',
    inRegistry: false,
    usedIn: 'SC-00 A1',
  },
];

export const byName = (part: string): Org => {
  const org = orgs.find((o) => o.name.includes(part));
  if (!org) throw new Error(`Нет фикстуры: ${part}`);
  return org;
};
