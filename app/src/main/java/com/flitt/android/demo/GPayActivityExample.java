package com.flitt.android.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.flitt.android.Cloudipsp;
import com.flitt.android.CloudipspWebView;
import com.flitt.android.GooglePayButton.GooglePayButton;
import com.flitt.android.GooglePayCall;
import com.flitt.android.Order;
import com.flitt.android.Receipt;

public class GPayActivityExample extends Activity implements
        Cloudipsp.PayCallback, // Implementing Cloudipsp.PayCallback for payment callbacks
        Cloudipsp.GooglePayCallback { // Implementing Cloudipsp.GooglePayCallback for Google Pay callbacks

    private static final int RC_GOOGLE_PAY = 100500;
    private static final String K_GOOGLE_PAY_CALL = "google_pay_call";
    private CloudipspWebView webView;
    private GooglePayButton googlePayButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpay);

        webView = findViewById(R.id.webView);
        googlePayButton = findViewById(R.id.googlePayButton);
        googlePayButton.setMerchantId(1549901);
//        googlePayButton.setToken("7c02ea6a07368697c3506fba03397041f8ec3704");
        googlePayButton.setRequestCode(RC_GOOGLE_PAY);
        googlePayButton.setOrder(createOrder());
        googlePayButton.initialize(webView);
        // Restore state
        if (savedInstanceState != null) {
            GooglePayCall googlePayCall = savedInstanceState.getParcelable(K_GOOGLE_PAY_CALL);
            googlePayButton.setGooglePayCall(googlePayCall);
        }
    }

    private Order createOrder() {
        final int amount = 100;
        final String email = "test@gmail.com";
        final String description = "test payment";
        final String currency = "GEL";
        return new Order(amount, currency, "vb_" + System.currentTimeMillis(), description, email);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(K_GOOGLE_PAY_CALL, googlePayButton.getGooglePayCall());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_PAY) {
            googlePayButton.handleActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onPaidProcessed(Receipt receipt) {
        Toast.makeText(this, "Paid " + receipt.status.name() + "\nPaymentId:" + receipt.paymentId, Toast.LENGTH_LONG).show();
        Log.d("PaymentStatus", "Paid " + receipt.status.name() + " PaymentId: " + receipt.paymentId);
    }

    @Override
    public void onPaidFailure(Cloudipsp.Exception e) {
        if (e instanceof Cloudipsp.Exception.Failure) {
            Cloudipsp.Exception.Failure f = (Cloudipsp.Exception.Failure) e;
            Toast.makeText(this, "Failure\nErrorCode: " + f.errorCode + "\nMessage: " + f.getMessage() + "\nRequestId: " + f.requestId, Toast.LENGTH_LONG).show();
        } else if (e instanceof Cloudipsp.Exception.NetworkSecurity) {
            Toast.makeText(this, "Network security error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } else if (e instanceof Cloudipsp.Exception.ServerInternalError) {
            Toast.makeText(this, "Internal server error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } else if (e instanceof Cloudipsp.Exception.NetworkAccess) {
            Toast.makeText(this, "Network error", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Payment Failed", Toast.LENGTH_LONG).show();
        }
        e.printStackTrace();
    }

    @Override
    public void onGooglePayInitialized(GooglePayCall result) {
        Log.d("GooglePay", "Google Pay initialized : " + result.toString());
        Toast.makeText(this, "Google Pay initialized", Toast.LENGTH_LONG).show();
        googlePayButton.setGooglePayCall(result);
    }

}
