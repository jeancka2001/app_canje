package com.tickets.canjeapp;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {

    // ── Cambia esta URL por la de tu servidor ─────────────────────────────────
    private static final String BASE_URL = "https://api.t-ickets.com/mikroti";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String mensaje);
    }
 
    /**
     * Busca el boleto por el ID del item de localidad (QR code = localidades_items.id).
     * Endpoint: GET /Boleteria/BoletoScan/{itemId}
     * Respuesta exitosa: { estado: true, boleto: { id_ticket, cedula, localidad, sillas, valor, canje, silla, fila, nombre_localidad } }
     */
    public static void buscarBoleto(String itemId, Callback<JSONObject> callback) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/Boleteria/BoletoScan/" + itemId)
                        .get()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject json = new JSONObject(body);
                    if (json.optBoolean("estado", false)) {
                        callback.onSuccess(json.getJSONObject("boleto"));
                    } else {
                        callback.onError(json.optString("mensaje", "Boleto no encontrado"));
                    }
                }
            } catch (IOException e) {
                callback.onError("Error de red: " + e.getMessage());
            } catch (Exception e) {
                callback.onError("Error inesperado: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Canjea el boleto usando el id de ticket_usuarios.
     * Endpoint: POST /Boleteria/canjeticke2/{ticketId}
     * Body: { "cedula": "...", "info": {} }
     * Respuesta exitosa (HTTP 200): { estado: true, mensaje: "Se ha canjeado X correctamente." }
     * Respuesta si ya canjeado (HTTP 409): { estado: false, mensaje: "El código no es válido o ya fue canjeado" }
     */
    public static void canjearBoleto(String ticketId, String cedula, Callback<String> callback) {
        new Thread(() -> {
            try {
                JSONObject bodyJson = new JSONObject();
                bodyJson.put("cedula", cedula);
                bodyJson.put("info", new JSONObject());

                RequestBody body = RequestBody.create(bodyJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(BASE_URL + "/Boleteria/canjeticke2/" + ticketId)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    JSONObject json = new JSONObject(responseBody);
                    if (response.code() == 200 && json.optBoolean("estado", false)) {
                        callback.onSuccess(json.optString("mensaje", "Canjeado correctamente"));
                    } else {
                        callback.onError(json.optString("mensaje", "No se pudo canjear el boleto"));
                    }
                }
            } catch (IOException e) {
                callback.onError("Error de red: " + e.getMessage());
            } catch (Exception e) {
                callback.onError("Error inesperado: " + e.getMessage());
            }
        }).start();
    }
}
