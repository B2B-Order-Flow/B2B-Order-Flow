import { useQuery } from '@tanstack/react-query';
import { dictionariesApi, queryKeys } from '../../api/endpoints';
import type { DictionaryItem, StageType } from '../../api/types';
import { texts } from '../../texts/ru';

const OTHER = 'OTHER';

/**
 * Причины отклонения своего шага из GET /dictionaries/rejection-reasons?stageType=…
 * (core-api.yaml, getDictionary; business-process.md §10.2). OTHER — общий код для всех шагов:
 * интерфейс добавляет его сам, если в ответе его нет. При сбое остаётся только OTHER.
 */
export function useRejectionReasons(stageType: StageType) {
  // Повторная ТВ использует список шага TECH_CHECK (business-process.md §10.2)
  const step: StageType = stageType === 'RECHECK_TECH' ? 'TECH_CHECK' : stageType;
  const query = useQuery({
    queryKey: queryKeys.rejectionReasons(step),
    queryFn: () => dictionariesApi.rejectionReasons(step),
    retry: false,
    staleTime: 10 * 60_000,
  });
  const items: DictionaryItem[] = (query.data?.items ?? []).filter((item) => item.code !== OTHER);
  const other: DictionaryItem = query.data?.items.find((item) => item.code === OTHER) ?? {
    code: OTHER,
    title: texts.reasons.other,
    requiresComment: true,
  };
  const all = [...items, other];
  return {
    loading: query.isPending,
    options: all.map((item) => ({ value: item.code, label: item.title })),
    /** Комментарий обязателен к OTHER и к кодам, у которых так указано в справочнике. */
    requiresComment: (code: string) =>
      code === OTHER || Boolean(all.find((item) => item.code === code)?.requiresComment),
  };
}
