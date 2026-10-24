package com.opx.oss.appintro.slides;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.textfield.TextInputEditText;
import com.opx.oss.R;
import com.opx.oss.appintro.AppIntroActivity;
import com.opx.oss.appintro.IntroPage;
import com.opx.oss.license.LicenseManager;

/**
 * Intro slide for entering and activating the 12-character license key.
 * Requires an internet connection; the key is validated against the JSONBin
 * registry and bound to this device on success.
 */
public class SlideLicense extends Fragment implements IntroPage {

    private TextInputEditText input;
    private TextView status;
    private boolean busy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.new_slide_license, container, false);
        input = view.findViewById(R.id.license_input);
        status = view.findViewById(R.id.license_status);
        input.addTextChangedListener(new KeyFormatter());
        return view;
    }

    @Override
    public CharSequence primaryLabel(Context context) {
        return context.getString(R.string.intro_action_activate);
    }

    @Override
    public boolean primaryEnabled() {
        return !busy && input != null && LicenseManager.looksValid(
                input.getText() == null ? "" : input.getText().toString());
    }

    @Override
    public void onPrimary() {
        if (busy || input == null) return;
        String raw = input.getText() == null ? "" : input.getText().toString();
        if (!LicenseManager.looksValid(raw)) return;
        busy = true;
        refreshPrimary();
        showStatus(R.string.license_status_checking);
        final Context ctx = requireContext();
        final Activity act = getActivity();
        new Thread(() -> {
            LicenseManager.Activation result;
            boolean networkError = false;
            try {
                result = LicenseManager.activate(ctx, raw);
            } catch (Exception e) {
                result = null;
                networkError = true;
            }
            final LicenseManager.Activation finalResult = result;
            final boolean finalNetwork = networkError;
            if (act != null && !act.isFinishing()) {
                act.runOnUiThread(() -> onActivationDone(finalResult, finalNetwork));
            }
        }).start();
    }

    private void onActivationDone(LicenseManager.Activation result, boolean networkError) {
        if (!isAdded()) return;
        busy = false;
        refreshPrimary();
        if (networkError) {
            showStatus(R.string.license_status_offline);
            return;
        }
        switch (result) {
            case ACTIVATED:
            case ALREADY_ACTIVE:
                showStatus(R.string.license_status_ok);
                advance();
                break;
            case INVALID:
                showStatus(R.string.license_status_invalid);
                break;
            case BANNED:
                showStatus(R.string.license_status_banned);
                break;
            case USED_ELSEWHERE:
                showStatus(R.string.license_status_in_use);
                break;
        }
    }

    private void advance() {
        Activity activity = requireActivity();
        ViewPager2 pager = activity.findViewById(R.id.view_pager);
        if (activity instanceof AppIntroActivity && pager != null) {
            ((AppIntroActivity) activity).advanceFromLicense();
        }
    }

    private void refreshPrimary() {
        if (getActivity() instanceof AppIntroActivity) {
            ((AppIntroActivity) getActivity()).refreshPrimary();
        }
    }

    private void showStatus(int resId) {
        if (status == null || !isAdded()) return;
        status.setText(resId);
        status.setVisibility(View.VISIBLE);
    }

    private final class KeyFormatter implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (input == null) return;
            String normalized = LicenseManager.normalizeKey(s.toString());
            String formatted = formatForInput(normalized);
            if (!formatted.equals(s.toString())) {
                input.removeTextChangedListener(this);
                input.setText(formatted);
                input.setSelection(formatted.length());
                input.addTextChangedListener(this);
            }
            refreshPrimary();
        }

        private String formatForInput(String normalized) {
            if (normalized == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < normalized.length(); i++) {
                if (i > 0 && i % 4 == 0 && sb.length() < 14) sb.append('-');
                sb.append(normalized.charAt(i));
            }
            return sb.toString();
        }
    }
}
