package com.flitt.android.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.flitt.android.demo.R;

/**
 * Created by vberegovoy on 6/20/17.
 */

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onSimpleExampleClicked(View view) {
        startActivity(new Intent(this, SimpleExampleActivity.class));
    }

    public void onFlexibleExampleClicked(View view) {
        startActivity(new Intent(this, FlexibleExampleActivity.class));
    }
    public void onGPayActivityExampleClicked(View view) {
        startActivity(new Intent(this, GPayActivityExample.class));
    }
    public void onPayBankExampleClick(View view) {
        startActivity(new Intent(this, BankPaymentActivity.class));
    }
}
