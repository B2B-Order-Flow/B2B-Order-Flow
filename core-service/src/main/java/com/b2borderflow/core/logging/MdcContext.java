package com.b2borderflow.core.logging;

import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.MDC;

/**
 * Перенос MDC в задачу, выполняемую в другом потоке. Виртуальные потоки
 * не наследуют MDC автоматически, а без этого {@code correlationId} терялся бы
 * в вызовах mock-сервисов.
 */
public final class MdcContext {

    private MdcContext() {
    }

    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(callerContext);
            try {
                return supplier.get();
            } finally {
                apply(previous);
            }
        };
    }

    private static void apply(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
