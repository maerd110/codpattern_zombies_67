package com.cdp.codpattern.app.zombies.service;

import net.minecraft.network.chat.Component;
import java.util.*;

/** Keep the failing configuration location and cause across startup rollback. */
public final class ZombiesStartupDiagnostics {
    private ZombiesStartupDiagnostics() { }

    public static void check(String location, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException failure) { throw new IllegalArgumentException(location + ": " + reason(failure), failure); }
    }

    public static String reason(Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var messages = new ArrayList<String>();
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && !message.isBlank() && !messages.contains(message)) messages.add(message);
        }
        return messages.isEmpty() ? failure.getClass().getSimpleName() : String.join(" → ", messages);
    }

    private static String param(ZombiesServiceResult<?> result, String key) {
        return result == null || !result.params().containsKey(key) ? "" : result.params().get(key).value();
    }

    public static Component message(ZombiesServiceResult<?> result) {
        String code = result == null ? "unknown" : result.code().key();
        var details = new ArrayList<String>();
        for (String key : List.of("firstIssueCode", "firstIssueSubject", "participant", "stage", "reason")) {
            String value = param(result, key).replaceAll("[\\r\\n\\t]+", " ");
            if (!value.isBlank() && !details.contains(value)) details.add(value);
        }
        if (details.isEmpty()) return Component.translatable("message.codpattern.zombies.startup_failed", code);
        String detail = String.join(" · ", details);
        if (detail.length() > 1200) detail = detail.substring(0, 1200) + "…";
        return Component.translatable("message.codpattern.zombies.startup_failed_with_issue", code, detail);
    }
}
