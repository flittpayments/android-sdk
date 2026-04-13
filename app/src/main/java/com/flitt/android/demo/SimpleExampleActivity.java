package com.flitt.android.demo;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.flitt.android.Card;
import com.flitt.android.CardInputView;
import com.flitt.android.FeeCalculationResponse;

public class SimpleExampleActivity extends BaseExampleActivity {
    private CardInputView cardInput;
    private EditText editAmount;
    private Spinner spinnerCcy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cardInput = findViewById(R.id.card_input);
        editAmount = findViewById(R.id.edit_amount);
        spinnerCcy = findViewById(R.id.spinner_ccy);

        if (editAmount != null) {
            editAmount.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    updateFeeParams();
                }
            });
        }

        if (spinnerCcy != null) {
            spinnerCcy.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateFeeParams();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        updateFeeParams();
//        if (BuildConfig.DEBUG) {
//            cardInput.setHelpedNeeded(true);
//        }
    }


    private void updateFeeParams() {
        final String amountStr = editAmount.getText().toString().trim();
        final String currency = spinnerCcy.getSelectedItem() != null
                ? spinnerCcy.getSelectedItem().toString()
                : null;

        if (amountStr.isEmpty() || currency == null) {
            return;
        }

        final int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            return;
        }

        cardInput.setFeeParams(
                4055775,
                amount,
                currency,
                new CardInputView.FeeCallbackWithError() {
                    @Override
                    public void onFeeResult(FeeCalculationResponse response) {}
                    @Override
                    public void onFeeError(Exception e) {}
                }
        );
    }
    @Override
    protected int getLayoutResId() {
        return R.layout.activity_simple_example;
    }

    @Override
    protected Card getCard() {
        return cardInput.confirm(new CardInputView.ConfirmationErrorHandler() {
            @Override
            public void onCardInputErrorClear(CardInputView view, EditText editText) {

            }

            @Override
            public void onCardInputErrorCatched(CardInputView view, EditText editText, String error) {

            }
        });
    }
}