package com.flitt.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.flitt.android.Bank;
import com.flitt.android.BankRedirectDetails;
import com.flitt.android.Cloudipsp;
import com.flitt.android.CloudipspWebView;
import com.flitt.android.Order;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BankPaymentActivity extends Activity implements Cloudipsp.BankPayCallback {
    private static final String TAG = "MainActivity";
    private static final String CURRENT_TOKEN = "8a1e18cf0722c618bfc1cda8f07e9e7c1327f31b";
    private static final int MERCHANT_ID = 1549901;

    private CloudipspWebView webView;
    private Cloudipsp cloudipsp;
    private ListView bankListView;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_list);

        // Initialize thread pool with a fixed number of threads
        executorService = Executors.newFixedThreadPool(2);

        cloudipsp = new Cloudipsp(MERCHANT_ID);

        // Setup bank list view
        bankListView = findViewById(R.id.bankListView);
        bankListView.setOnItemClickListener((parent, view, position, id) -> {
            Bank selectedBank = (Bank) parent.getItemAtPosition(position);
            initiatePayment(selectedBank);
        });

        // Fetch available banks
        fetchBankList();
    }

    private void fetchBankList() {
        executorService.execute(() -> {
            try {
                // Fetch bank list
                Order order = createOrder();

//                List<Bank> banks = cloudipsp.getAvailableBankList(CURRENT_TOKEN, this);
                List<Bank> banks = cloudipsp.getAvailableBankList(order, this);

                // Update UI on main thread
                runOnUiThread(() -> {
                    if (banks != null && !banks.isEmpty()) {
                        BankAdapter adapter = new BankAdapter(this, banks);
                        bankListView.setAdapter(adapter);
                    } else {
                        showToast("No banks available");
                    }
                });
            } catch (Exception e) {
                // Handle errors on main thread
                runOnUiThread(() -> {
                    Log.e(TAG, "Error getting bank list", e);
                    showToast("Error: " + e.getMessage());
                });
            }
        });
    }

    private void initiatePayment(@NonNull final Bank bank) {
        showToast("Processing payment with " + bank.getName());

        executorService.execute(() -> {
            try {
                // Create order
                Order order = createOrder();

                // Initiate bank payment
                cloudipsp.initiateBankPayment(
                        this,  // Context
                        order,              // Order details
                        bank,               // Selected bank
                        this,  // Callback interface
                        true                // auto navigate
                );
            } catch (Exception e) {
                // Handle payment initiation errors on main thread
                runOnUiThread(() -> {
                    Log.e(TAG, "Error initiating payment", e);
                    showToast("Payment error: " + e.getMessage());
                });
            }
        });
    }

    private Order createOrder() {
        return new Order(
                100,                            // Amount
                "GEL",                          // Currency
                "order_" + System.currentTimeMillis(), // Unique order ID
                "Test Payment",                 // Description
                "test@gmail.com"                // Customer email
        );
    }

    @Override
    public void onRedirected(@NonNull final BankRedirectDetails bankRedirectDetails) {
        runOnUiThread(() -> {
            showToast("Payment processed successfully!");
            Log.d(TAG, "Redirect URL: " + bankRedirectDetails.getUrl());
        });
    }

    @Override
    public void onPaidFailure(@NonNull final Cloudipsp.Exception e) {
        runOnUiThread(() -> {
            showToast("Payment failed: " + e.getMessage());
            Log.e(TAG, "Payment failure", e);
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown executor service to prevent memory leaks
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}