package com.tickets.canjeapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScannedCodesStore {

    private static final List<ScannedCodeEntry>        entries = new ArrayList<>();
    private static final Map<String, ScannedCodeEntry> byCode  = new LinkedHashMap<>();

    /** Agrega o retorna la entrada existente para ese código. */
    public static synchronized ScannedCodeEntry add(String code) {
        if (byCode.containsKey(code)) return byCode.get(code);
        ScannedCodeEntry entry = new ScannedCodeEntry(code, System.currentTimeMillis());
        byCode.put(code, entry);
        entries.add(0, entry);
        return entry;
    }

    public static List<ScannedCodeEntry> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public static int getCount() {
        return entries.size();
    }

    public static synchronized int getCanjeadosCount() {
        int n = 0;
        for (ScannedCodeEntry e : entries)
            if (ScannedCodeEntry.ESTADO_CANJEADO.equals(e.estado)) n++;
        return n;
    }

    public static synchronized void clear() {
        entries.clear();
        byCode.clear();
    }
}
