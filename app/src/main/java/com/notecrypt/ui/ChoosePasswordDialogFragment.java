package com.notecrypt.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.notecrypt.utils.PasswordPolicy;
import com.notecryptpro.R;

/** Asks for a new master password, twice, and checks it against the password policy. */
public class ChoosePasswordDialogFragment extends DialogFragment {

    public interface Callback {
        void onPasswordChosen(String purpose, char[] password);
    }

    public static final String PURPOSE_CREATE = "create";
    public static final String PURPOSE_CHANGE = "change";

    private static final String ARG_PURPOSE = "purpose";

    private EditText fieldNew;
    private EditText fieldConfirm;

    public static ChoosePasswordDialogFragment newInstance(final String purpose) {
        final ChoosePasswordDialogFragment fragment = new ChoosePasswordDialogFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_PURPOSE, purpose);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(requireContext()).inflate(R.layout.new_password, null);
        fieldNew = view.findViewById(R.id.field_password);
        fieldConfirm = view.findViewById(R.id.conf_field_password);
        final String purpose = getArguments() == null ? PURPOSE_CHANGE : getArguments().getString(ARG_PURPOSE);
        final int title = PURPOSE_CREATE.equals(purpose) ? R.string.title_createPassword : R.string.title_changeMasterPass;
        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setTitle(title)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit());
        }
    }

    private void submit() {
        final String first = fieldNew.getText().toString();
        final String second = fieldConfirm.getText().toString();
        switch (PasswordPolicy.check(first)) {
            case TOO_SHORT:
                fieldNew.setError(getString(R.string.pw_too_short, PasswordPolicy.MIN_LENGTH));
                return;
            case TOO_SIMPLE:
                fieldNew.setError(getString(R.string.pw_too_simple));
                return;
            default:
                break;
        }
        if (!first.equals(second)) {
            fieldConfirm.setError(getString(R.string.toast_wrongNewPassword));
            return;
        }
        final char[] password = first.toCharArray();
        fieldNew.setText("");
        fieldConfirm.setText("");
        final String purpose = getArguments() == null ? PURPOSE_CHANGE : getArguments().getString(ARG_PURPOSE);
        dismiss();
        ((Callback) requireActivity()).onPasswordChosen(purpose, password);
    }
}
