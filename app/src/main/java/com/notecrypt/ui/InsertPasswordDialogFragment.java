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

import com.notecryptpro.R;

/** Asks for the password of an existing vault. */
public class InsertPasswordDialogFragment extends DialogFragment {

    public interface Callback {
        void onPasswordEntered(String fileName, char[] password);
    }

    private static final String ARG_FILE = "file";

    private EditText field;

    public static InsertPasswordDialogFragment newInstance(final String fileName) {
        final InsertPasswordDialogFragment fragment = new InsertPasswordDialogFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_FILE, fileName);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(requireContext()).inflate(R.layout.insert_password, null);
        field = view.findViewById(R.id.field_password);
        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setTitle(R.string.title_password)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            // Override the default click so an empty field keeps the dialog open.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit());
        }
    }

    private void submit() {
        final String text = field.getText().toString();
        if (text.isEmpty()) {
            field.setError(getString(R.string.toast_emptyPassword));
            return;
        }
        final char[] password = text.toCharArray();
        field.setText("");
        final String fileName = getArguments() == null ? null : getArguments().getString(ARG_FILE);
        dismiss();
        ((Callback) requireActivity()).onPasswordEntered(fileName, password);
    }
}
