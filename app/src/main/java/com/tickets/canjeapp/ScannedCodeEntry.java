package com.tickets.canjeapp;

public class ScannedCodeEntry {
    public static final String ESTADO_BUSCANDO   = "BUSCANDO";
    public static final String ESTADO_ENCONTRADO = "ENCONTRADO";
    public static final String ESTADO_CANJEADO   = "CANJEADO";
    public static final String ESTADO_ERROR      = "ERROR";

    public final String codigo;
    public final long   timestamp;

    public String idTicket  = "—";
    public String cedula    = "—";
    public String localidad = "—";
    public String silla     = "—";
    public String fila      = "—";
    public String valor     = "—";
    public String concierto = "—";
    public String estado    = ESTADO_BUSCANDO;
    public String errorMsg  = "";

    public ScannedCodeEntry(String codigo, long timestamp) {
        this.codigo    = codigo;
        this.timestamp = timestamp;
    }
}
