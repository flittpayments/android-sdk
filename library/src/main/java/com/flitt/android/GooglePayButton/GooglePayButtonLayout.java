package com.flitt.android.GooglePayButton;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.flitt.android.Cloudipsp;
import com.flitt.android.GooglePayCall;
import com.flitt.android.Order;
import com.flitt.android.Receipt;

/**
 * Base layout class providing the foundation for Google Pay Button configuration
 */
public class GooglePayButtonLayout extends FrameLayout {
    protected GooglePayCall googlePayCall;
    protected OnButtonClickListener onButtonClickListener;
    protected int buttonType;
    protected int buttonTheme;
    protected int cornerRadiusDp;
    protected Cloudipsp.GooglePayMerchantConfig gpayConfig;
    protected Receipt receipt;
    protected int merchantId;
    protected Order order;
    protected String token;
    protected int RC_GOOGLE_PAY;

    public GooglePayButtonLayout(Context context) {
        super(context);
    }

    public GooglePayButtonLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GooglePayButtonLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    // Getter and Setter methods
    public void setGooglePayCall(GooglePayCall googlePayCall) {
        this.googlePayCall = googlePayCall;
    }

    public GooglePayCall getGooglePayCall() {
        return googlePayCall;
    }

    public void setOnButtonClickListener(OnButtonClickListener listener) {
        this.onButtonClickListener = listener;
    }

    public void setButtonType(ButtonType type) {
        if (type != null) {
            this.buttonType = type.getValue();
        }
    }

    public void setButtonTheme(String theme) {
        this.buttonTheme = "light".equalsIgnoreCase(theme) ? 2 : 1;
    }

    public void setCornerRadiusDp(int radiusDp) {
        this.cornerRadiusDp = radiusDp;
    }

    public void setGpayConfig(Cloudipsp.GooglePayMerchantConfig config) {
        this.gpayConfig = config;
    }
    public void setReceipt(Receipt receipt) {
        this.receipt = receipt;
    }

    public void setMerchantId(int merchantId) {
        this.merchantId = merchantId;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setRequestCode(int requestCode) {
        this.RC_GOOGLE_PAY = requestCode;
    }

    protected int dpToPx(int dp) {
        float scale = this.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }

    // Button type enum for easier configuration
    public enum ButtonType {
        BUY(1),
        BOOK(2),
        CHECKOUT(3),
        DONATE(4),
        ORDER(5),
        PAY(6),
        PLAIN(8),
        SUBSCRIBE(7);
        private final int value;

        ButtonType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // Interface for button click events
    public interface OnButtonClickListener {
        void onGooglePayButtonClicked();
    }
}
