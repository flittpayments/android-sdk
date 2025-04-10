package com.flitt.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.CertPathValidatorException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

/**
 * Created by vberegovoy on 09.11.15.
 */
public final class Cloudipsp {
    private static final String HOST = BuildConfig.API_HOST;
    private static final String URL_CALLBACK = "http://callback";
    private static final SimpleDateFormat DATE_AND_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
    private static final SSLSocketFactory tlsSocketFactory = Tls12SocketFactory.getInstance();
    static boolean strictUiBlocking = true;

    static {
        DATE_AND_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final Handler sMain = new Handler(Looper.getMainLooper());

    public final int merchantId;
    private CloudipspView cloudipspView;

    public Cloudipsp(int merchantId) {
        this.merchantId = merchantId;
    }
    public Cloudipsp(int merchantId, CloudipspView cloudipspView) {
        this.merchantId = merchantId;
        this.cloudipspView = cloudipspView;
    }

    public interface Callback {
        void onPaidFailure(Exception e);
    }

    public interface PayCallback extends Callback {
        void onPaidProcessed(Receipt receipt);
    }

    public interface BankPayCallback extends Callback {
        void onRedirected(BankRedirectDetails bankRedirectDetails);
    }

    public interface GooglePayCallback extends Callback {
        void onGooglePayInitialized(GooglePayCall result);
    }

    public static boolean supportsGooglePay(Context context) {
        if (!isGooglePayRuntimeProvided()) {
            return false;
        }

        return GoogleApiAvailability
                .getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    public void pay(final Card card, final Order order, PayCallback callback) {
        if (!card.isValidCard()) {
            throw new IllegalArgumentException("Card should be valid");
        }

        new PayTask(callback) {
            @Override
            public void runInTry() throws java.lang.Exception {
                final String token = getToken(order, card);
                final Checkout checkout = checkout(card, token, order.email, URL_CALLBACK);
                payContinue(token, checkout, callback);
            }
        }.start();
    }

    public void payToken(final Card card, final String token, PayCallback callback) {
        if (!card.isValidCard()) {
            throw new IllegalArgumentException("Card should be valid");
        }

        new PayTask(callback) {
            @Override
            public void runInTry() throws java.lang.Exception {
                final JSONObject receipt = ajaxInfo(token);
                final JSONObject orderData = receipt.getJSONObject("order_data");
                final Checkout checkout = checkout(card, token, orderData.optString("email", null), receipt.getString("response_url"));
                payContinue(token, checkout, callback);
            }
        }.start();
    }

    private static boolean isGooglePayRuntimeProvided() {
        try {
            Class.forName("com.google.android.gms.common.GoogleApiAvailability");
            Class.forName("com.google.android.gms.wallet.PaymentDataRequest");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void googlePayInitialize(final String token,
                                    final Activity activity,
                                    final int requestCode,
                                    final GooglePayCallback googlePayCallback,
                                    final GooglePayMerchantConfig googlePayConfig,
                                    final Receipt receipt
    ) {
        final GooglePayMetaInfo metaInfo = new GooglePayMetaInfo(token, null, receipt.amount, receipt.currency, receipt.responseUrl);
        googlePayInitialize(activity, requestCode, googlePayCallback, googlePayConfig, metaInfo);
    }

    public void googlePayInitialize(final String token,
                                    final Activity activity,
                                    final int requestCode,
                                    final GooglePayCallback googlePayCallback) {
        googlePayInitialize(activity, requestCode, googlePayCallback, new GooglePayMetaInfoProvider() {
            @Override
            public GooglePayMetaInfo getGooglePayMetaInfo() throws java.lang.Exception {
                final Receipt receipt = order(token);
                return new GooglePayMetaInfo(token, null, receipt.amount, receipt.currency, receipt.responseUrl);
            }
        });
    }


    public List<Bank> getAvailableBankList(final String token, BankPayCallback bankPayCallback) throws java.lang.Exception {
        try {
            final TreeMap<String, Object> mobilePayRequest = new TreeMap<>();
            mobilePayRequest.put("token", token);
            final JSONObject mobilePayResponse = callJson("/api/checkout/ajax/info", mobilePayRequest);
            List<Bank> banks = new ArrayList<>();
            if (mobilePayResponse.has("tabs")) {
                JSONObject tabs = mobilePayResponse.getJSONObject("tabs");
                if (tabs.has("trustly")) {
                    JSONObject trustly = tabs.getJSONObject("trustly");
                    if (trustly.has("payment_systems")) {
                        JSONObject paymentSystems = trustly.getJSONObject("payment_systems");
                        Iterator<String> keys = paymentSystems.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (paymentSystems.get(key) instanceof JSONObject) {
                                JSONObject bankData = paymentSystems.getJSONObject(key);
                                Bank bank = new Bank(
                                        key,
                                        bankData.getInt("country_priority"),
                                        bankData.getInt("user_priority"),
                                        bankData.getBoolean("quick_method"),
                                        bankData.getBoolean("user_popular"),
                                        bankData.getString("name"),
                                        bankData.getString("country"),
                                        bankData.getString("bank_logo"),
                                        bankData.getString("alias")
                                );
                                banks.add(bank);
                            }
                        }
                    }
                }
            }
            Collections.sort(banks, new Comparator<Bank>() {
                @Override
                public int compare(Bank bank1, Bank bank2) {
                    if (bank1.getUserPriority() != bank2.getUserPriority()) {
                        return Integer.compare(bank2.getUserPriority(), bank1.getUserPriority());
                    }
                    return Integer.compare(bank2.getCountryPriority(), bank1.getCountryPriority());
                }
            });
            return banks;
        } catch (Exception e) {
            if (bankPayCallback != null) {
                bankPayCallback.onPaidFailure(e);
            }
            throw new Exception(e);
        }
    }

    public List<Bank> getAvailableBankList(final Order order, BankPayCallback bankPayCallback) throws java.lang.Exception {
        try {
            final String token = getToken(order, null);
            return this.getAvailableBankList(token, bankPayCallback);
        } catch (Exception e) {
            if (bankPayCallback != null) {
                bankPayCallback.onPaidFailure(e);
            }
            throw new Exception(e);
        }
    }

    public void initiateBankPayment(Context context, String token, Bank bank, BankPayCallback bankPayCallback,boolean autoRedirect) {
        try {
            DeviceInfoProvider deviceInfo = new DeviceInfoProvider(context);
            String encodedDeviceData = deviceInfo.getEncodedDeviceFingerprint();
            // Create payment request data
            final TreeMap<String, Object> requestObj = new TreeMap<>();
            final JSONObject receipt = ajaxInfo(token);
            final JSONObject orderData = receipt.getJSONObject("order_data");
            requestObj.put("merchant_id", orderData.get("merchant_id"));
            requestObj.put("amount", orderData.get("amount"));
            requestObj.put("currency", orderData.get("currency"));
            requestObj.put("token", token);
            requestObj.put("payment_system", bank.getBankId());
            requestObj.put("kkh", encodedDeviceData);
            Log.d("deviceInfo", "deviceInfo: " + encodedDeviceData);
            final JSONObject response = callJson("/api/checkout/ajax", requestObj);
            Log.d("response", "response: " + response);
            String responseStatus = response.optString("response_status", "");
            String action = response.optString("action", "");
            if ("success".equals(responseStatus) && "redirect".equals(action) && response.has("url")) {
                String redirectUrl = response.getString("url");
                String target = response.optString("target", "_top");
                handleRedirect(context, redirectUrl, target, bankPayCallback,response,autoRedirect);
            } else {
                if (bankPayCallback != null) {
                    bankPayCallback.onPaidFailure(new Exception("Payment initiation failed: " +
                           "payment status: " + responseStatus + ", action: " + action));
                }
            }
        } catch (java.lang.Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void initiateBankPayment(Context context, final Order order, Bank bank, BankPayCallback bankPayCallback,boolean autoRedirect) throws java.lang.Exception {
        try {
            final String token = getToken(order, null);
            this.initiateBankPayment(context, token,bank,bankPayCallback,autoRedirect);
        } catch (Exception e) {
            if (bankPayCallback != null) {
                bankPayCallback.onPaidFailure(e);
            }
            throw new Exception(e);
        }
    }


    private void handleRedirect(Context context, String url, String target, BankPayCallback payCallback,JSONObject response,boolean autoRedirect) throws JSONException {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if ("_blank".equals(target)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else if ("_top".equals(target) || "_self".equals(target)) {
            // Use default behavior — no additional flags needed
        }
        BankRedirectDetails bankRedirectDetails = new BankRedirectDetails(
                response.getString("action"),
                response.getString("url"),
                response.getString("target"),
                response.getString("response_status")
        );
        if(!autoRedirect){
            payCallback.onRedirected(bankRedirectDetails);
            return;
        }
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            payCallback.onRedirected(bankRedirectDetails);
        } else {
            if (payCallback != null) {
                payCallback.onPaidFailure(new Exception("No application available to handle payment URL"));
            }
        }
    }

    public void googlePayInitialize(final Order order,
                                    final Activity activity,
                                    final int requestCode,
                                    final GooglePayCallback googlePayCallback,
                                    final GooglePayMerchantConfig googlePayConfig
    ) {
        final GooglePayMetaInfo metaInfo = new GooglePayMetaInfo(null, order, order.amount, order.currency, URL_CALLBACK);
        googlePayInitialize(activity, requestCode, googlePayCallback, googlePayConfig, metaInfo);
    }

    public void googlePayInitialize(final Order order,
                                    final Activity activity,
                                    final int requestCode,
                                    final GooglePayCallback googlePayCallback) {
        googlePayInitialize(activity, requestCode, googlePayCallback, new GooglePayMetaInfoProvider() {
            @Override
            public GooglePayMetaInfo getGooglePayMetaInfo() throws java.lang.Exception {
                return new GooglePayMetaInfo(null, order, order.amount, order.currency, URL_CALLBACK);
            }
        });
    }

    public class GooglePayMerchantConfigResult {
        private GooglePayMerchantConfig merchantConfig;
        private Receipt receipt;

        public GooglePayMerchantConfigResult(GooglePayMerchantConfig merchantConfig, Receipt receipt) {
            this.merchantConfig = merchantConfig;
            this.receipt = receipt;
        }

        public GooglePayMerchantConfig getMerchantConfig() {
            return merchantConfig;
        }

        public Receipt getReceipt() {
            return receipt;
        }
    }

    public GooglePayMerchantConfigResult googlePayInitializeMerchantConfig(final String token) throws java.lang.Exception {
        final Receipt receipt = order(token);
        final GooglePayMetaInfo metaInfo = new GooglePayMetaInfo(token, null, receipt.amount, receipt.currency, receipt.responseUrl);
        final GooglePayMerchantConfig config = googlePayMerchantConfig(metaInfo);
        return new GooglePayMerchantConfigResult(config, receipt);
    }

    public GooglePayMerchantConfig googlePayInitializeMerchantConfig(final Order order) throws java.lang.Exception {
        final GooglePayMetaInfo metaInfo = new GooglePayMetaInfo(null, order, order.amount, order.currency, URL_CALLBACK);
        return googlePayMerchantConfig(metaInfo);
    }

    private static class GooglePayMetaInfo {
        private final String token;
        private final Order order;
        private final int amount;
        private final String currency;
        private final String callbackUrl;

        private GooglePayMetaInfo(String token, Order order, int amount, String currency, String callbackUrl) {
            this.token = token;
            this.order = order;
            this.amount = amount;
            this.currency = currency;
            this.callbackUrl = callbackUrl;
        }
    }

    private interface GooglePayMetaInfoProvider {
        GooglePayMetaInfo getGooglePayMetaInfo() throws java.lang.Exception;
    }

    private void googlePayInitialize(final Activity activity,
                                     final int requestCode,
                                     final GooglePayCallback googlePayCallback,
                                     final GooglePayMerchantConfig googlePayConfig,
                                     final GooglePayMetaInfo metaInfo) {

        if (!isGooglePayRuntimeProvided()) {
            return;
        }

        executeGooglePayProcess(activity, requestCode, googlePayCallback, googlePayConfig, metaInfo);
    }

    private void googlePayInitialize(final Activity activity,
                                     final int requestCode,
                                     final GooglePayCallback googlePayCallback,
                                     final GooglePayMetaInfoProvider metaInfoProvider) {

        if (!isGooglePayRuntimeProvided()) {
            return;
        }

        new Task<GooglePayCallback>(googlePayCallback) {
            @Override
            public void runInTry() throws java.lang.Exception {
                final GooglePayMetaInfo metaInfo = metaInfoProvider.getGooglePayMetaInfo();
                final GooglePayMerchantConfig googlePayConfig = googlePayMerchantConfig(metaInfo);

                executeGooglePayProcess(activity, requestCode, callback, googlePayConfig, metaInfo);
            }
        }.start();
    }

    private void executeGooglePayProcess(final Activity activity,
                                         final int requestCode,
                                         final GooglePayCallback googlePayCallback,
                                         final GooglePayMerchantConfig googlePayConfig,
                                         final GooglePayMetaInfo metaInfo) {

        new Task<GooglePayCallback>(new GooglePayCallback() {
            @Override
            public void onGooglePayInitialized(final GooglePayCall result) {
                sMain.post(new Runnable() {
                    @Override
                    public void run() {
                        googlePayCallback.onGooglePayInitialized(result);
                    }
                });
            }

            @Override
            public void onPaidFailure(final Exception e) {
                sMain.post(new Runnable() {
                    @Override
                    public void run() {
                        googlePayCallback.onPaidFailure(e);
                    }
                });
            }
        }) {
            @Override
            public void runInTry() throws java.lang.Exception {
                final PaymentDataRequest request = PaymentDataRequest.fromJson(googlePayConfig.data.toString());

                final PaymentsClient paymentsClient = Wallet.getPaymentsClient(activity,
                        new Wallet.WalletOptions.Builder()
                                .setEnvironment(googlePayConfig.data.getString("environment").equals("PRODUCTION")
                                        ? WalletConstants.ENVIRONMENT_PRODUCTION
                                        : WalletConstants.ENVIRONMENT_TEST
                                )
                                .build());

                callback.onGooglePayInitialized(new GooglePayCall(
                        metaInfo.token,
                        metaInfo.order,
                        metaInfo.callbackUrl,
                        googlePayConfig.paymentSystem
                ));

                AutoResolveHelper.resolveTask(
                        paymentsClient.loadPaymentData(request),
                        activity,
                        requestCode);
            }
        }.start();
    }

    public static class GooglePayMerchantConfig {
        public final String paymentSystem;
        public final JSONObject data;

        private GooglePayMerchantConfig(String paymentSystem, JSONObject data) {
            this.paymentSystem = paymentSystem;
            this.data = data;
        }
    }

    private GooglePayMerchantConfig googlePayMerchantConfig(GooglePayMetaInfo metaInfo) throws java.lang.Exception {
        final TreeMap<String, Object> mobilePayRequest = new TreeMap<>();
        mobilePayRequest.put("merchant_id", merchantId);
        if (metaInfo.token == null) {
            mobilePayRequest.put("amount", metaInfo.amount);
            mobilePayRequest.put("currency", metaInfo.currency);
        } else {
            mobilePayRequest.put("token", metaInfo.token);
        }

        final JSONObject mobilePayResponse = callJson("/api/checkout/ajax/mobile_pay", mobilePayRequest);
        if (mobilePayResponse.has("error_message")) {
            handleResponseError(mobilePayResponse);
        }
        final String paymentSystem = mobilePayResponse.getString("payment_system");

        final JSONArray methodsJson = mobilePayResponse.getJSONArray("methods");
        JSONObject data = null;
        for (int i = methodsJson.length() - 1; i >= 0; --i) {
            final JSONObject methodJson = methodsJson.getJSONObject(i);
            if ("https://google.com/pay".equals(methodJson.getString("supportedMethods"))) {
                data = methodJson.getJSONObject("data");
                break;
            }
        }
        if (data == null) {
            throw new Exception.GooglePayUnsupported();
        }
        return new GooglePayMerchantConfig(paymentSystem, data);
    }

    public boolean googlePayComplete(int resultCode, Intent data, final GooglePayCall googlePayCall, PayCallback payCallback) {
        if (Activity.RESULT_CANCELED == resultCode) {
            return false;
        }
        if (data == null) {
            throw new NullPointerException("data should be not null");
        }
        if (googlePayCall == null) {
            throw new NullPointerException("googlePayCall should be not null");
        }
        if (payCallback == null) {
            throw new NullPointerException("payCallback should be not null");
        }
        if (Activity.RESULT_OK == resultCode) {
            final PaymentData paymentData = PaymentData.getFromIntent(data);
            new PayTask(payCallback) {
                @Override
                public void runInTry() throws java.lang.Exception {
                    if (googlePayCall.order != null) {
                        final String token = getToken(googlePayCall.order, null);
                        final Checkout checkout = checkoutGooglePay(
                                token,
                                googlePayCall.paymentSystem,
                                googlePayCall.order.email,
                                googlePayCall.callbackUrl,
                                paymentData
                        );
                        payContinue(token, checkout, callback);
                    } else if (googlePayCall.token != null) {
                        final Checkout checkout = checkoutGooglePay(
                                googlePayCall.token,
                                googlePayCall.paymentSystem,
                                null,
                                googlePayCall.callbackUrl,
                                paymentData
                        );
                        payContinue(googlePayCall.token, checkout, callback);
                    }
                }
            }.start();
        } else if (AutoResolveHelper.RESULT_ERROR == resultCode) {
            final Status status = AutoResolveHelper.getStatusFromIntent(data);
            payCallback.onPaidFailure(new Exception.GooglePayFailure(status));
        }
        return true;
    }

    private interface RunInTry {
        void runInTry() throws java.lang.Exception;
    }

    private static void runInTry(RunInTry runInTry, Callback callback) {
        try {
            runInTry.runInTry();
        } catch (CertPathValidatorException | SSLHandshakeException e) {
            callback.onPaidFailure(new Exception.NetworkSecurity(e.getMessage()));
        } catch (FileNotFoundException e) {
            callback.onPaidFailure(new Exception.ServerInternalError(e));
        } catch (IOException e) {
            callback.onPaidFailure(new Exception.NetworkAccess(e.getMessage()));
        } catch (Exception e) {
            callback.onPaidFailure(e);
        } catch (JSONException e) {
            callback.onPaidFailure(new Exception.IllegalServerResponse(e));
        } catch (java.lang.Exception e) {
            callback.onPaidFailure(new Exception.Unknown(e));
        }
    }

    private abstract static class Task<C extends Callback> extends Thread implements RunInTry {
        final C callback;

        private Task(C callback) {
            this.callback = callback;
        }

        @Override
        public final void run() {
            Cloudipsp.runInTry(this, callback);
        }
    }

    private abstract static class PayTask extends Task<PayCallback> {
        PayTask(final PayCallback payCallback) {
            super(new PayCallback() {
                @Override
                public void onPaidProcessed(final Receipt receipt) {
                    sMain.post(new Runnable() {
                        @Override
                        public void run() {
                            payCallback.onPaidProcessed(receipt);
                        }
                    });
                }

                @Override
                public void onPaidFailure(final Exception e) {
                    sMain.post(new Runnable() {
                        @Override
                        public void run() {
                            payCallback.onPaidFailure(e);
                        }
                    });
                }
            });
        }
    }

    public String getToken(Order order, Card card) throws java.lang.Exception {
        final TreeMap<String, Object> request = new TreeMap<String, Object>();

        request.put("order_id", order.id);
        request.put("merchant_id", String.valueOf(merchantId));
        request.put("order_desc", order.description);
        request.put("amount", String.valueOf(order.amount));
        request.put("currency", order.currency);
        if (!TextUtils.isEmpty(order.productId)) {
            request.put("product_id", order.productId);
        }
        if (!TextUtils.isEmpty(order.paymentSystems)) {
            request.put("payment_systems", order.paymentSystems);
        }
        if (!TextUtils.isEmpty(order.defaultPaymentSystem)) {
            request.put("default_payment_system", order.defaultPaymentSystem);
        }
        if (order.lifetime != -1) {
            request.put("lifetime", order.lifetime);
        }
        if (TextUtils.isEmpty(order.merchantData)) {
            request.put("merchant_data", "[]");
        } else {
            request.put("merchant_data", order.merchantData);
        }
        if (!TextUtils.isEmpty(order.version)) {
            request.put("version", order.version);
        }
        if (!TextUtils.isEmpty(order.serverCallbackUrl)) {
            request.put("server_callback_url", order.serverCallbackUrl);
        }
        if (card != null && Card.SOURCE_NFC == card.source) {
            request.put("reservation_data", "eyJ0eXBlIjoibmZjX21vYmlsZSJ9");
        } else {
            if (!TextUtils.isEmpty(order.reservationData)) {
                request.put("reservation_data", order.reservationData);
            }
        }
        if (order.lang != null) {
            request.put("lang", order.lang.name());
        }
        request.put("preauth", order.preauth ? "Y" : "N");
        request.put("required_rectoken", order.requiredRecToken ? "Y" : "N");
        request.put("verification", order.verification ? "Y" : "N");
        request.put("verification_type", order.verificationType.name());
        request.putAll(order.arguments);
        request.put("response_url", URL_CALLBACK);
        request.put("delayed", order.delayed ? "Y" : "N");

        final JSONObject response = call("/api/checkout/token", request);
        final String token = response.getString("token");
        return token;
    }

    private static class Checkout {
        static final int WITHOUT_3DS = 0;
        static final int WITH_3DS = 1;

        final SendData sendData;
        final String url;
        final int action;
        final String callbackUrl;

        private Checkout(SendData sendData, String url, int action, String callbackUrl) {
            this.sendData = sendData;
            this.url = url;
            this.action = action;
            this.callbackUrl = callbackUrl;
        }

        private static class SendData {
            final String md;
            final String paReq;
            final String termUrl;

            private SendData(String md, String paReq, String termUrl) {
                this.md = md;
                this.paReq = paReq;
                this.termUrl = termUrl;
            }
        }
    }

    private static Checkout checkout(Card card, String token, String email, String callbackUrl) throws java.lang.Exception {
        final TreeMap<String, Object> request = new TreeMap<>();
        request.put("card_number", card.cardNumber);
        if (card.source == Card.SOURCE_FORM) {
            request.put("cvv2", card.cvv);
        }
        request.put("expiry_date", String.format(Locale.US, "%02d%02d", card.mm, card.yy));
        request.put("payment_system", "card");
        request.put("token", token);
        if (email != null) {
            request.put("email", email);
        }
        return checkoutContinue(request, callbackUrl);
    }

    private static Checkout checkoutGooglePay(String token, String paymentSystem,
                                              String email, String callbackUrl,
                                              PaymentData paymentData) throws java.lang.Exception {
        final TreeMap<String, Object> request = new TreeMap<>();
        request.put("payment_system", paymentSystem);
        request.put("token", token);
        request.put("email", email);

        final JSONObject data = new JSONObject(paymentData.toJson());
        request.put("data", data);

        return checkoutContinue(request, callbackUrl);
    }

    private static Checkout checkoutContinue(TreeMap<String, Object> request, String callbackUrl) throws java.lang.Exception {
        final JSONObject response = call("/api/checkout/ajax", request);
        final String url = response.getString("url");
        if (url.startsWith(callbackUrl)) {
            return new Checkout(null, url, Checkout.WITHOUT_3DS, callbackUrl);
        } else {
            final JSONObject sendData = response.getJSONObject("send_data");

            return new Checkout
                    (
                            new Checkout.SendData
                                    (
                                            sendData.getString("MD"),
                                            sendData.getString("PaReq"),
                                            sendData.getString("TermUrl")
                                    ),
                            url,
                            Checkout.WITH_3DS,
                            callbackUrl
                    );
        }
    }

    private void payContinue(final String token, final Checkout checkout, final PayCallback callback) throws java.lang.Exception {
        final RunInTry orderChecker = new RunInTry() {
            @Override
            public void runInTry() throws java.lang.Exception {
                final Receipt receipt = order(token);
                callback.onPaidProcessed(receipt);
            }
        };

        if (checkout.action == Checkout.WITHOUT_3DS) {
            Cloudipsp.runInTry(orderChecker, callback);
        } else {
            url3ds(token, checkout, callback);
        }
    }

    private static Receipt order(String token) throws java.lang.Exception {
        final TreeMap<String, Object> request = new TreeMap<>();
        request.put("token", token);
        final JSONObject response = call("/api/checkout/merchant/order", request);
        final JSONObject orderData = response.getJSONObject("order_data");
        return parseOrder(orderData, response.getString("response_url"));
    }

    private static JSONObject ajaxInfo(String token) throws java.lang.Exception {
        final TreeMap<String, Object> request = new TreeMap<>();
        request.put("token", token);
        return call("/api/checkout/ajax/info", request);
    }

    private static Receipt parseOrder(JSONObject orderData, String responseUrl) throws JSONException {
        Card.Type cardType;
        try {
            cardType = Card.Type.valueOf(orderData.getString("card_type").toUpperCase());
        } catch (IllegalArgumentException e) {
            cardType = Card.Type.UNKNOWN;
        }

        Date recTokenLifeTime;
        try {
            recTokenLifeTime = DATE_AND_FORMAT.parse(orderData.getString("rectoken_lifetime"));
        } catch (java.lang.Exception e) {
            recTokenLifeTime = null;
        }
        Date settlementDate;
        try {
            settlementDate = DATE_FORMAT.parse(orderData.getString("settlement_date"));
        } catch (java.lang.Exception e) {
            settlementDate = null;
        }
        final String verificationStatus = orderData.optString("verification_status");
        final Receipt.VerificationStatus verificationStatusEnum;
        if (TextUtils.isEmpty(verificationStatus)) {
            verificationStatusEnum = null;
        } else {
            verificationStatusEnum = Receipt.VerificationStatus.valueOf(verificationStatus);
        }

        return new Receipt
                (
                        orderData.getString("masked_card"),
                        orderData.getString("card_bin"),
                        Integer.valueOf(orderData.getString("amount")),
                        orderData.getInt("payment_id"),
                        orderData.getString("currency"),
                        Receipt.Status.valueOf(orderData.getString("order_status")),
                        Receipt.TransationType.valueOf(orderData.getString("tran_type")),
                        orderData.getString("sender_cell_phone"),
                        orderData.getString("sender_account"),
                        cardType,
                        orderData.getString("rrn"),
                        orderData.getString("approval_code"),
                        orderData.getString("response_code"),
                        orderData.getString("product_id"),
                        orderData.getString("rectoken"),
                        recTokenLifeTime,
                        orderData.optInt("reversal_amount", -1),
                        orderData.optInt("settlement_amount", -1),
                        orderData.optString("settlement_currency"),
                        settlementDate,
                        orderData.optInt("eci", -1),
                        orderData.optInt("fee", -1),
                        orderData.optInt("actual_amount", -1),
                        orderData.optString("actual_currency"),
                        orderData.optString("payment_system"),
                        verificationStatusEnum,
                        orderData.getString("signature"),
                        responseUrl,
                        orderData
                );
    }

    private static JSONObject call(String path, TreeMap<String, Object> request) throws java.lang.Exception {
        final JSONObject json = callJson(path, request);
        checkResponse(json);
        return json;
    }

    private static JSONObject callJson(String path, TreeMap<String, Object> request) throws java.lang.Exception {
        final JSONObject body = new JSONObject();
        try {
            body.put("request", new JSONObject(request));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        final String jsonOfResponse = call(HOST + path, body.toString(), "application/json");
        if (BuildConfig.DEBUG) {
            Log.i("Cloudipsp", "Read: " + jsonOfResponse);
        }
        return new JSONObject(jsonOfResponse).getJSONObject("response");
    }

    private static void checkResponse(JSONObject response) throws java.lang.Exception {
        if (!response.getString("response_status").equals("success")) {
            handleResponseError(response);
        }
    }

    private static void handleResponseError(JSONObject response) throws java.lang.Exception {
        throw new Exception.Failure
                (
                        response.getString("error_message"),
                        response.getInt("error_code"),
                        response.getString("request_id")
                );
    }

    private void url3ds(final String token, final Checkout checkout, final PayCallback callback) throws java.lang.Exception {
        final Rs rs;
        final String[] contentType = new String[1];
        final ResponseInterceptor interceptor = new ResponseInterceptor() {
            @Override
            public void onIntercept(HttpURLConnection httpURLConnection) {
                contentType[0] = httpURLConnection.getHeaderField("Content-Type");
            }
        };
        if (TextUtils.isEmpty(checkout.sendData.paReq)) {
            final JSONObject sendData = new JSONObject();
            sendData.put("MD", checkout.sendData.md);
            sendData.put("PaReq", checkout.sendData.paReq);
            sendData.put("TermUrl", checkout.sendData.termUrl);
            rs = callRaw(checkout.url, sendData.toString(), "application/json", interceptor);
        } else {
            final String urlEncoded =
                    "MD=" + URLEncoder.encode(checkout.sendData.md, "UTF-8") + "&" +
                            "PaReq=" + URLEncoder.encode(checkout.sendData.paReq, "UTF-8") + "&" +
                            "TermUrl=" + URLEncoder.encode(checkout.sendData.termUrl, "UTF-8");

            rs = callRaw(checkout.url, urlEncoded, "application/x-www-form-urlencoded", interceptor);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1 || TextUtils.isEmpty(contentType[0])) {
            contentType[0] = "text/html";
        }

        final CloudipspView.PayConfirmation confirmation = new CloudipspView.PayConfirmation(
                rs.content,
                contentType[0],
                checkout.url,
                checkout.callbackUrl,
                HOST,
                rs.cookie,
                new CloudipspView.PayConfirmation.Listener() {
                    @Override
                    public void onConfirmed(final JSONObject response) {
                        runInTry(new RunInTry() {
                            @Override
                            public void runInTry() throws java.lang.Exception {
                                if (response == null) {
                                    new Task<PayCallback>(callback) {
                                        @Override
                                        public void runInTry() throws java.lang.Exception {
                                            callback.onPaidProcessed(order(token));
                                        }
                                    }.start();
                                } else {
                                    if (!response.getString("url").startsWith(checkout.callbackUrl)) {
                                        throw new java.lang.Exception();
                                    }
                                    final JSONObject orderData = response.getJSONObject("params");
                                    checkResponse(orderData);
                                    callback.onPaidProcessed(parseOrder(orderData, null));
                                }
                            }
                        }, callback);
                    }

                    @Override
                    public void onNetworkAccessError(String description) {
                        callback.onPaidFailure(new Exception.NetworkAccess(description));
                    }

                    @Override
                    public void onNetworkSecurityError(String description) {
                        callback.onPaidFailure(new Exception.NetworkSecurity(description));
                    }
                });

        sMain.post(new Runnable() {
            @Override
            public void run() {
                cloudipspView.confirm(confirmation);
            }
        });
    }

    private static String call(String url, String content, String contentType) throws java.lang.Exception {
        return call(url, content, contentType, null);
    }

    private interface ResponseInterceptor {
        void onIntercept(HttpURLConnection httpURLConnection);
    }

    private static String call(String url, String content, String contentType, ResponseInterceptor responseInterceptor) throws java.lang.Exception {
        return callRaw(url, content, contentType, responseInterceptor).content;
    }

    private static Rs callRaw(String url, String content, String contentType, ResponseInterceptor responseInterceptor) throws java.lang.Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (connection instanceof HttpsURLConnection) {
            final HttpsURLConnection secureConnection = (HttpsURLConnection) connection;
            if (tlsSocketFactory != null) {
                secureConnection.setSSLSocketFactory(tlsSocketFactory);
            }
            secureConnection.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    final String peerHost = session.getPeerHost();
                    return hostname.equals(peerHost);
                }
            });
        }
        final byte[] sentBytes = content.getBytes();
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Content-Length", String.valueOf(sentBytes.length));
            connection.setRequestProperty("User-Agent", "Android-SDK");
            connection.setRequestProperty("SDK-OS", "android");
            connection.setRequestProperty("SDK-Version", "1.17.2");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            final OutputStream output = connection.getOutputStream();
            output.write(sentBytes);
            output.flush();
            if (BuildConfig.DEBUG) {
                Log.i("Cloudipsp", "Sent(" + url + "):" + content);
            }
            connection.connect();
            final int contentLength = connection.getHeaderFieldInt("ContentLength", 350);
            if (responseInterceptor != null) {
                responseInterceptor.onIntercept(connection);
            }
            final Rs rs = new Rs();

            final StringBuilder sb = new StringBuilder(contentLength);
            readAll(connection.getInputStream(), sb);
            rs.cookie = connection.getHeaderField("Set-Cookie");
            rs.content = sb.toString();
            return rs;
        } finally {
            connection.disconnect();
        }
    }

    private static class Rs {
        public String content;
        public String cookie;
    }

    private static void readAll(InputStream from, StringBuilder to) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(from, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            to.append(line);
            to.append('\n');
        }
    }

    public static class Exception extends java.lang.Exception {
        Exception() {
        }

        Exception(String detailMessage) {
            super(detailMessage);
        }

        Exception(Throwable throwable) {
            super(throwable);
        }

        public final static class Failure extends Exception {
            public final int errorCode;
            public final String requestId;

            Failure(String detailMessage, int errorCode, String requestId) {
                super(detailMessage);
                this.errorCode = errorCode;
                this.requestId = requestId;
            }
        }

        public final static class GooglePayUnsupported extends Exception {
        }

        public final static class GooglePayFailure extends Exception {
            public final Status status;

            private GooglePayFailure(Status status) {
                this.status = status;
            }
        }

        public final static class IllegalServerResponse extends Exception {
            IllegalServerResponse(Throwable throwable) {
                super(throwable);
            }
        }

        public final static class NetworkSecurity extends Exception {
            NetworkSecurity(String detailMessage) {
                super(detailMessage);
            }
        }

        public final static class NetworkAccess extends Exception {
            NetworkAccess(String detailMessage) {
                super(detailMessage);
            }
        }

        public final static class ServerInternalError extends Exception {
            ServerInternalError(Throwable throwable) {
                super(throwable);
            }
        }

        public final static class Unknown extends Exception {
            Unknown(Throwable throwable) {
                super(throwable);
            }
        }
    }

    public static void setStrictUiBlocking(boolean value) {
        strictUiBlocking = value;
    }
}
