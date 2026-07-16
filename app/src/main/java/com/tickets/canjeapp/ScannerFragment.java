package com.tickets.canjeapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.tickets.canjeapp.databinding.FragmentScannerBinding;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScannerFragment extends Fragment {

    private static final int REQUEST_CAMERA = 101;

    private FragmentScannerBinding binding;
    private DecoratedBarcodeView   barcodeView;
    private ToneGenerator          toneGen;
    private TicketAdapter          ticketAdapter;
    private boolean                cameraActive = false;
    private boolean                skipWatcher  = false;
    private String lastCameraCode = null;
    private long   lastCameraTime = 0L;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentScannerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle("Canje Scanner");
        }

        try { toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100); } catch (Throwable ignore) {}

        barcodeView = binding.barcodeScanner;

        ticketAdapter = new TicketAdapter();
        binding.listScannedCodes.setAdapter(ticketAdapter);

        refreshList();
        ensureInputFocus();

        binding.editCode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getAction() == KeyEvent.ACTION_UP
                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitCode();
                return true;
            }
            return false;
        });

        binding.editCode.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                submitCode();
                return true;
            }
            return false;
        });

        binding.editCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (skipWatcher || s == null || s.length() == 0) return;
                char last = s.charAt(s.length() - 1);
                if (last == ' ' || last == '\n' || last == '\r' || last == '\t') {
                    submitCode();
                }
            }
        });

        binding.btnEnter.setOnClickListener(v -> submitCode());

        binding.btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            } else {
                openCamera();
            }
        });

        binding.btnCloseCamera.setOnClickListener(v -> closeCamera());

        binding.btnClear.setOnClickListener(v -> {
            if (ScannedCodesStore.getCount() == 0) {
                Toast.makeText(getContext(), "No hay registros", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Limpiar registro")
                    .setMessage("¿Borrar los " + ScannedCodesStore.getCount() + " registros de esta sesión?")
                    .setPositiveButton("Limpiar", (d, w) -> {
                        ScannedCodesStore.clear();
                        refreshList();
                        setFeedback(false, "Listo para escanear", "—");
                        Toast.makeText(getContext(), "Registro borrado", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
    }

    // ── Lógica de escaneo ─────────────────────────────────────────────────────

    private void submitCode() {
        String raw = binding.editCode.getText() != null
                ? binding.editCode.getText().toString().trim() : "";
        skipWatcher = true;
        binding.editCode.setText("");
        skipWatcher = false;
        if (!raw.isEmpty()) processCode(raw);
        ensureInputFocus();
    }

    private void processCode(String code) {
        ScannedCodeEntry entry = ScannedCodesStore.add(code);
        entry.estado = ScannedCodeEntry.ESTADO_BUSCANDO;
        setFeedback(true, "Buscando boleto...", code);
        try { if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 80); } catch (Throwable ignore) {}
        refreshList();

        ApiClient.buscarBoleto(code, new ApiClient.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject boleto) {
                if (!isAdded() || getActivity() == null) return;
                entry.idTicket  = boleto.optString("id_ticket", "—");
                entry.cedula    = boleto.optString("cedula", "—");
                entry.localidad = boleto.optString("nombre_localidad", boleto.optString("localidad", "—"));
                entry.silla     = boleto.optString("silla", boleto.optString("sillas", "—"));
                entry.fila      = boleto.optString("fila", "—");
                entry.valor     = boleto.optString("valor", "—");
                entry.concierto = boleto.optString("concierto", "—");
                String canje    = boleto.optString("canje", "NO CANJEADO");
                entry.estado    = "CANJEADO".equalsIgnoreCase(canje)
                        ? ScannedCodeEntry.ESTADO_CANJEADO
                        : ScannedCodeEntry.ESTADO_ENCONTRADO;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    refreshList();
                    setFeedback(true, "Boleto encontrado", code);
                    mostrarDialogoBoleto(entry);
                });
            }

            @Override
            public void onError(String mensaje) {
                if (!isAdded() || getActivity() == null) return;
                entry.estado   = ScannedCodeEntry.ESTADO_ERROR;
                entry.errorMsg = mensaje;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    refreshList();
                    setFeedback(false, mensaje, code);
                    try { if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 300); } catch (Throwable ignore) {}
                    Toast.makeText(getContext(), mensaje, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void mostrarDialogoBoleto(ScannedCodeEntry entry) {
        if (getContext() == null) return;

        boolean yaCanjeado = ScannedCodeEntry.ESTADO_CANJEADO.equals(entry.estado);

        String info = "Concierto:  " + entry.concierto + "\n"
                    + "Localidad:  " + entry.localidad + "\n"
                    + "Silla:      " + entry.silla + "\n"
                    + "Fila:       " + entry.fila + "\n"
                    + "Valor:      $" + entry.valor + "\n"
                    + "Cédula:     " + entry.cedula + "\n"
                    + "Estado:     " + (yaCanjeado ? "CANJEADO" : "DISPONIBLE");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Boleto #" + entry.idTicket)
                .setMessage(info)
                .setNegativeButton("Cerrar", null)
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "CANJEAR", (d, w) -> {});
        dialog.show();

        Button btnCanjear = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (yaCanjeado) {
            btnCanjear.setEnabled(false);
            btnCanjear.setText("YA CANJEADO");
        } else {
            btnCanjear.setBackgroundColor(0xFF2E7D32);
            btnCanjear.setTextColor(0xFFFFFFFF);
            btnCanjear.setOnClickListener(v -> {
                btnCanjear.setEnabled(false);
                btnCanjear.setText("Canjeando...");
                ejecutarCanje(entry, dialog);
            });
        }
    }

    private void ejecutarCanje(ScannedCodeEntry entry, AlertDialog dialog) {
        ApiClient.canjearBoleto(entry.idTicket, entry.cedula, new ApiClient.Callback<String>() {
            @Override
            public void onSuccess(String mensaje) {
                if (!isAdded() || getActivity() == null) return;
                entry.estado = ScannedCodeEntry.ESTADO_CANJEADO;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    refreshList();
                    setFeedback(true, "CANJEADO ✓", entry.idTicket);
                    try { if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 200); } catch (Throwable ignore) {}
                    dialog.dismiss();
                    Toast.makeText(getContext(), "✓ " + mensaje, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String mensaje) {
                if (!isAdded() || getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    setFeedback(false, "Error al canjear", entry.idTicket);
                    try { if (toneGen != null) toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 400); } catch (Throwable ignore) {}
                    Toast.makeText(getContext(), "✗ " + mensaje, Toast.LENGTH_LONG).show();
                    Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) { btn.setEnabled(true); btn.setText("CANJEAR"); }
                });
            }
        });
    }

    private void setFeedback(boolean ok, String msg, String code) {
        int color = ok ? 0xFF2E7D32 : 0xFFD32F2F;
        binding.panelFeedback.setBackgroundColor(color);
        binding.txtFeedback.setText(msg);
        binding.txtCodeDisplay.setText(code);
    }

    private void refreshList() {
        ticketAdapter.notifyDataSetChanged();
        int total     = ScannedCodesStore.getCount();
        int canjeados = ScannedCodesStore.getCanjeadosCount();
        binding.txtEscaneados.setText(total + (total == 1 ? " escaneado" : " escaneados"));
        binding.txtCanjeados.setText(canjeados + (canjeados == 1 ? " canjeado" : " canjeados"));
    }

    // ── Cámara ────────────────────────────────────────────────────────────────

    private void openCamera() {
        if (cameraActive) return;
        cameraActive = true;
        binding.layoutInput.setVisibility(View.GONE);
        binding.btnCamera.setVisibility(View.GONE);
        binding.barcodeScanner.setVisibility(View.VISIBLE);
        binding.btnCloseCamera.setVisibility(View.VISIBLE);

        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result == null || result.getText() == null) return;
                String code = result.getText().trim();
                if (code.isEmpty()) return;
                long now = System.currentTimeMillis();
                if (code.equals(lastCameraCode) && (now - lastCameraTime) < 3000) return;
                lastCameraCode = code;
                lastCameraTime = now;
                requireActivity().runOnUiThread(() -> processCode(code));
            }
            @Override public void possibleResultPoints(List<ResultPoint> resultPoints) {}
        });
        barcodeView.resume();
    }

    private void closeCamera() {
        cameraActive = false;
        try { barcodeView.pause(); } catch (Throwable ignore) {}
        binding.barcodeScanner.setVisibility(View.GONE);
        binding.btnCloseCamera.setVisibility(View.GONE);
        binding.btnCamera.setVisibility(View.VISIBLE);
        binding.layoutInput.setVisibility(View.VISIBLE);
        ensureInputFocus();
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private void ensureInputFocus() {
        try {
            binding.editCode.requestFocus();
            binding.editCode.post(() -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 21)
                        binding.editCode.setShowSoftInputOnFocus(false);
                    InputMethodManager imm = (InputMethodManager) requireContext()
                            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(binding.editCode.getWindowToken(), 0);
                } catch (Throwable ignore) {}
            });
        } catch (Throwable ignore) {}
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQUEST_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(getContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (cameraActive) { try { barcodeView.resume(); } catch (Throwable ignore) {} }
        else ensureInputFocus();
    }

    @Override
    public void onPause() {
        super.onPause();
        try { barcodeView.pause(); } catch (Throwable ignore) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try { if (toneGen != null) toneGen.release(); } catch (Throwable ignore) {}
        toneGen = null;
        binding = null;
    }

    // ── Adapter del registro ──────────────────────────────────────────────────

    private class TicketAdapter extends BaseAdapter {
        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        @Override public int getCount() { return ScannedCodesStore.getCount(); }
        @Override public ScannedCodeEntry getItem(int pos) { return ScannedCodesStore.getAll().get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_scanned_code, parent, false);
            }
            ScannedCodeEntry e = getItem(pos);

            TextView txtTime      = convertView.findViewById(R.id.txtItemTime);
            TextView txtEstado    = convertView.findViewById(R.id.txtItemEstado);
            TextView txtConcierto = convertView.findViewById(R.id.txtItemConcierto);
            TextView txtLocalidad = convertView.findViewById(R.id.txtItemLocalidad);
            TextView txtCedula    = convertView.findViewById(R.id.txtItemCedula);
            TextView txtValor     = convertView.findViewById(R.id.txtItemValor);

            txtTime.setText(sdf.format(new Date(e.timestamp)) + "  " + e.codigo);

            switch (e.estado) {
                case ScannedCodeEntry.ESTADO_CANJEADO:
                    txtEstado.setText("CANJEADO");
                    txtEstado.setBackgroundColor(0xFF2E7D32);
                    break;
                case ScannedCodeEntry.ESTADO_ENCONTRADO:
                    txtEstado.setText("SIN CANJEAR");
                    txtEstado.setBackgroundColor(0xFFF57C00);
                    break;
                case ScannedCodeEntry.ESTADO_BUSCANDO:
                    txtEstado.setText("BUSCANDO...");
                    txtEstado.setBackgroundColor(0xFF757575);
                    break;
                default:
                    txtEstado.setText("NO ENCONTRADO");
                    txtEstado.setBackgroundColor(0xFFD32F2F);
                    break;
            }

            txtConcierto.setText(e.concierto.equals("—") ? "QR: " + e.codigo : e.concierto);
            txtLocalidad.setText("Localidad: " + e.localidad + "  |  Silla: " + e.silla + "  |  Fila: " + e.fila);
            txtCedula.setText("Cédula: " + e.cedula);
            txtValor.setText("$" + e.valor);

            return convertView;
        }
    }
}
