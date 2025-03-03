package com.flitt.android.GooglePayButton;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.flitt.android.Cloudipsp.GooglePayMerchantConfigResult;
import com.flitt.android.Cloudipsp;
import com.flitt.android.CloudipspWebView;
import com.flitt.android.GooglePayCall;
import com.flitt.android.Order;
import com.flitt.android.Receipt;
import com.google.android.gms.wallet.button.ButtonOptions;
import com.google.android.gms.wallet.button.PayButton;

import org.json.JSONException;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class GooglePayButton extends GooglePayButtonLayout {
    private static final String TAG = "GooglePayButton";

    private PayButton payButton;
    private Cloudipsp cloudipsp;
    private CloudipspWebView webView;
    private Cloudipsp.GooglePayCallback googlePayCallback;
    private Cloudipsp.PayCallback payCallback;

    // Thread management
    private final Executor backgroundExecutor;
    private final Handler mainHandler;

    // Resource cleanup tracking
    private boolean isInitialized = false;

    public GooglePayButton(Context context) {
        super(context);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        initializeCallbacks(context);
    }

    public GooglePayButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        initializeCallbacks(context);
    }

    public GooglePayButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        initializeCallbacks(context);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Clean up resources when view is detached
        cleanup();
    }

    private void cleanup() {
        // Stop any ongoing operations
        mainHandler.removeCallbacksAndMessages(null);
        isInitialized = false;
    }

    private void initializeCallbacks(Context context) {
        if (context instanceof Cloudipsp.GooglePayCallback) {
            this.googlePayCallback = (Cloudipsp.GooglePayCallback) context;
        }
        if (context instanceof Cloudipsp.PayCallback) {
            this.payCallback = (Cloudipsp.PayCallback) context;
        }
    }

    /**
     * Initialize the Google Pay button with the provided web view
     */
    public void initialize(CloudipspWebView webView) {
        if (isInitialized) {
            return;
        }

        if (webView == null) {
            Log.e(TAG, "WebView cannot be null");
            return;
        }

        this.webView = webView;
        this.cloudipsp = new Cloudipsp(this.merchantId, this.webView);

        validateConfiguration();
        initializeGooglePay();
        isInitialized = true;
    }

    private void validateConfiguration() {
        if (merchantId <= 0) {
            Log.e(TAG, "Invalid merchant ID");
        }

        boolean hasToken = token != null && !token.isEmpty();
        boolean hasOrder = order != null;

        if (!hasToken && !hasOrder) {
            Log.e(TAG, "Either token or order must be provided");
        }
    }

    private void initializeGooglePay() {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Cloudipsp.GooglePayMerchantConfig config;
                    final Receipt receipt; // Make it final

                    if (token != null && !token.isEmpty()) {
                        GooglePayMerchantConfigResult result = cloudipsp.googlePayInitializeMerchantConfig(token);
                        config = result.getMerchantConfig();
                        receipt = result.getReceipt();
                    } else if (order != null) {
                        config = cloudipsp.googlePayInitializeMerchantConfig(order);
                        receipt = null;
                    } else {
                        Log.e(TAG, "Both token and order are null");
                        return;
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (receipt != null) {
                                setReceipt(receipt);
                            }
                            if (config != null) {
                                setGpayConfig(config);
                                buildAndAttachButton();
                            } else {
                                Log.e(TAG, "Failed to initialize Google Pay configuration");
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing Google Pay: " + e.getMessage());

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleInitializationError(e);
                        }
                    });
                }
            }
        });
    }

    private void handleInitializationError(Exception e) {
        // Hide button if initialization fails
        setVisibility(GONE);

        // Notify via callback if available
        if (googlePayCallback != null && e instanceof Cloudipsp.Exception) {
            googlePayCallback.onPaidFailure((Cloudipsp.Exception) e);
        }
    }

    private void buildAndAttachButton() {
        removeExistingButton();
        createPayButton();
        configurePayButton();
        addView(payButton);
        requestLayout();
    }

    private void removeExistingButton() {
        if (this.payButton != null) {
            this.removeView(this.payButton);
            this.payButton = null;
        }
    }

    private void createPayButton() {
        this.payButton = new PayButton(this.getContext());
    }

    private void configurePayButton() {
        try {
            if (gpayConfig == null || gpayConfig.data == null) {
                Log.e(TAG, "Google Pay configuration not initialized");
                return;
            }

            String paymentMethods = gpayConfig.data.getString("allowedPaymentMethods");
            ButtonOptions options = ButtonOptions.newBuilder()
                    .setButtonType(this.buttonType)
                    .setButtonTheme(this.buttonTheme)
                    .setCornerRadius(dpToPx(this.cornerRadiusDp))
                    .setAllowedPaymentMethods(paymentMethods)
                    .build();

            this.payButton.initialize(options);

            this.payButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        initiateGooglePay();
                    } catch (Exception e) {
                        Log.e(TAG, "Error initiating Google Pay: " + e.getMessage());
                    }
                }
            });

            checkGooglePaySupport();
        } catch (JSONException e) {
            Log.e(TAG, "Error configuring Google Pay Button: " + e.getMessage());
        }
    }

    private void initiateGooglePay() throws Exception {
        Cloudipsp.GooglePayCallback callback = new Cloudipsp.GooglePayCallback() {
            @Override
            public void onGooglePayInitialized(GooglePayCall result) {
                googlePayCall = result;
                if (googlePayCallback != null) {
                    googlePayCallback.onGooglePayInitialized(result);
                }
            }

            @Override
            public void onPaidFailure(Cloudipsp.Exception e) {
                if (payCallback != null) {
                    payCallback.onPaidFailure(e);
                }
            }
        };

        if (token != null && !token.isEmpty()) {
            //here need to passs meta info
            cloudipsp.googlePayInitialize(token, (Activity) getContext(), RC_GOOGLE_PAY, callback, gpayConfig,receipt);
        } else if (order != null) {
            cloudipsp.googlePayInitialize(order, (Activity) getContext(), RC_GOOGLE_PAY, callback, gpayConfig);
        } else {
            throw new IllegalStateException("Both token and order are null");
        }

        if (onButtonClickListener != null) {
            onButtonClickListener.onGooglePayButtonClicked();
        }
    }

    private void checkGooglePaySupport() {
        boolean isSupported = Cloudipsp.supportsGooglePay(getContext());
        setVisibility(isSupported ? VISIBLE : GONE);
    }

    /**
     * Handle activity result from Google Pay flow
     */
    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == RC_GOOGLE_PAY) {
            if (cloudipsp != null && googlePayCall != null) {
                cloudipsp.googlePayComplete(resultCode, data, googlePayCall, payCallback);
            } else {
                Log.e(TAG, "Cannot complete Google Pay transaction: cloudipsp or googlePayCall is null");
            }
        }
    }
    public static class Builder {
        private final Context context;
        private CloudipspWebView webView;
        private int merchantId;
        private String token;
        private Order order;
        private int requestCode = 1001;
        private ButtonType buttonType = ButtonType.PAY;
        private String buttonTheme = "dark";
        private int cornerRadius = 4;
        private OnButtonClickListener clickListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setWebView(CloudipspWebView webView) {
            this.webView = webView;
            return this;
        }

        public Builder setMerchantId(int merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Builder setToken(String token) {
            this.token = token;
            return this;
        }

        public Builder setOrder(Order order) {
            this.order = order;
            return this;
        }

        public Builder setRequestCode(int requestCode) {
            this.requestCode = requestCode;
            return this;
        }

        public Builder setButtonType(ButtonType buttonType) {
            this.buttonType = buttonType;
            return this;
        }

        public Builder setButtonTheme(String theme) {
            this.buttonTheme = theme;
            return this;
        }

        public Builder setCornerRadius(int radiusDp) {
            this.cornerRadius = radiusDp;
            return this;
        }

        public Builder setOnButtonClickListener(OnButtonClickListener listener) {
            this.clickListener = listener;
            return this;
        }

        public GooglePayButton build() {
            GooglePayButton button = new GooglePayButton(context);
            button.setMerchantId(merchantId);

            if (token != null && !token.isEmpty()) {
                button.setToken(token);
            }

            if (order != null) {
                button.setOrder(order);
            }

            button.setRequestCode(requestCode);
            button.setButtonType(buttonType);
            button.setButtonTheme(buttonTheme);
            button.setCornerRadiusDp(cornerRadius);

            if (clickListener != null) {
                button.setOnButtonClickListener(clickListener);
            }

            if (webView != null) {
                button.initialize(webView);
            }

            return button;
        }
    }

}