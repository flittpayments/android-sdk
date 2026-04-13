# Flitt Android SDK Integration Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Prerequisites](#prerequisites)
3. [Installation](#installation)
4. [Basic Implementation](#basic-implementation)
5. [Google Pay Integration](#google-pay-integration)
6. [Order Creation](#order-creation)
7. [Handling Payment Results](#handling-payment-results)
8. [Error Handling](#error-handling)
9. [Additional Configuration Options](#additional-configuration-options)
10. [Card Input & Fee Calculation](#card-input--fee-calculation)
11. [Sample Project Structure](#sample-project-structure)
12. [Bank Payment Methods](#bank-payment-methods)

## Introduction

The Flitt Android SDK provides an easy way to integrate payment processing capabilities into your Android application. This document outlines how to implement and use the key features of the Flitt SDK with a focus on Google Pay integration.

## Prerequisites

- Android Studio
- An Android project with minimum SDK version that supports Google Pay
- A Flitt merchant account with a valid token
- Google Pay API configured in your Google Developer account

## Installation

### Add the Flitt SDK to your project

Add the following to your app's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.flittpayments:android-sdk:1.0.0' // Replace with the latest version
}
```

## Basic Implementation

### 1. Add permissions to your AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 2. Create the layout for your payment screen

Create a layout file (e.g., `activity_gpay.xml`) that includes a WebView for the Flitt checkout and a container for the Google Pay button:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

   <FrameLayout
           android:id="@+id/googlePayButtonContainer"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:layout_margin="16dp" />

   <com.flittpayments.android.CloudipspWebView
           android:id="@+id/webView"
           android:layout_width="match_parent"
           android:layout_height="match_parent" />
</LinearLayout>
```

## Google Pay Integration

### 1. Implement the necessary interfaces

Your activity needs to implement `GooglePayCallback` for Google Pay initialization and `PayCallback` for payment processing:

```java
public class YourActivity extends AppCompatActivity implements GooglePayCallback, PayCallback {
    // Implementation follows
}
```

### 2. Initialize the Google Pay button

In your activity's `onCreate` method:

```java
private static final int RC_GOOGLE_PAY = 100500;
private static final String K_GOOGLE_PAY_CALL = "google_pay_call";
private CloudipspWebView webView;
private GooglePayButton googlePayButton;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_gpay);

    // Initialize the WebView
    webView = findViewById(R.id.webView);
    
    // Find the container for the Google Pay button
    FrameLayout buttonContainer = findViewById(R.id.googlePayButtonContainer);

    // Initialize the Google Pay button
    googlePayButton = new GooglePayButton.Builder(this)
            .setWebView(webView)
            .setMerchantId(YOUR_MERCHANT_ID)  // Replace with your merchant ID
            .setToken("YOUR_TOKEN")           // Replace with your token
            .setRequestCode(RC_GOOGLE_PAY)
            .setButtonTheme("light")          // Options: "light" or "dark"
            .setCornerRadius(10)              // Button corner radius in dp
            .build();

    // Set the button dimensions
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            (int) (300 * getResources().getDisplayMetrics().density),
            (int) (40 * getResources().getDisplayMetrics().density)
    );
    googlePayButton.setLayoutParams(params);
    
    // Add the button to the container
    buttonContainer.addView(googlePayButton);

    // Restore Google Pay state if available
    if (savedInstanceState != null) {
        GooglePayCall googlePayCall = savedInstanceState.getParcelable(K_GOOGLE_PAY_CALL);
        googlePayButton.setGooglePayCall(googlePayCall);
    }
}
```

### 3. Save and restore the Google Pay state

```java
@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(K_GOOGLE_PAY_CALL, googlePayButton.getGooglePayCall());
}
```

### 4. Handle the activity result

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == RC_GOOGLE_PAY) {
        googlePayButton.handleActivityResult(requestCode, resultCode, data);
    }
}
```

## Payment Methods

The Flitt SDK provides two different approaches for initiating payments:

### 1. Token-Based Payment

Using a token for payment is simpler but requires you to have an existing token from the Flitt backend:

```java
googlePayButton = new GooglePayButton.Builder(this)
        .setWebView(webView)
        .setMerchantId(YOUR_MERCHANT_ID)
        .setToken("YOUR_TOKEN")           // Use an existing token
        .setRequestCode(RC_GOOGLE_PAY)
        // ...other settings
        .build();
```

This approach is useful when:
- You have a pre-generated token from your server
- The payment amount and details are already defined on the server side
- You want to simplify the client-side implementation

### 2. Order-Based Payment

Creating an order object gives you more control over the payment details directly in your app:

```java
private Order createOrder() {
    final int amount = 100;               // Amount in the smallest currency unit (e.g., cents)
    final String email = "test@gmail.com"; // Customer email
    final String description = "test payment"; // Payment description
    final String currency = "USD";         // Currency code
    
    // Create a unique order ID using timestamp
    String orderId = "order_" + System.currentTimeMillis();
    
    return new Order(amount, currency, orderId, description, email);
}

// Use the order in the builder
googlePayButton = new GooglePayButton.Builder(this)
        .setWebView(webView)
        .setMerchantId(YOUR_MERCHANT_ID)
        .setOrder(createOrder())          // Use an order object
        .setRequestCode(RC_GOOGLE_PAY)
        // ...other settings
        .build();
```

This approach is beneficial when:
- You need to dynamically set the payment amount in the app
- You want to include specific customer information
- You need to generate order IDs client-side
- You require more detailed control over the payment parameters

### Important Note

You should use either `.setToken()` or `.setOrder()`, but not both simultaneously. The method you choose depends on your payment flow and backend integration.

## Handling Payment Results

Implement the callback methods to handle payment results:

```java
@Override
public void onPaidProcessed(Receipt receipt) {
    // Payment was successful
    Toast.makeText(this, "Paid " + receipt.status.name() + "\nPaymentId:" + receipt.paymentId, Toast.LENGTH_LONG).show();
    Log.d("PaymentStatus", "Paid " + receipt.status.name() + " PaymentId: " + receipt.paymentId);
    
    // Handle successful payment (update UI, navigate to success screen, etc.)
}

@Override
public void onGooglePayInitialized(GooglePayCall result) {
    // Google Pay was successfully initialized
    Log.d("GooglePay", "Google Pay initialized : " + result.toString());
    Toast.makeText(this, "Google Pay initialized", Toast.LENGTH_LONG).show();
    googlePayButton.setGooglePayCall(result);
}
```

## Error Handling

Implement the failure callback to handle payment errors:

```java
@Override
public void onPaidFailure(Cloudipsp.Exception e) {
   if (e instanceof Cloudipsp.Exception.Failure) {
      Cloudipsp.Exception.Failure f = (Cloudipsp.Exception.Failure) e;
      Toast.makeText(this, "Failure\nErrorCode: " + f.errorCode +
                      "\nMessage: " + f.getMessage() + "\nRequestId: " + f.requestId,
              Toast.LENGTH_LONG).show();
   } else if (e instanceof Cloudipsp.Exception.NetworkSecurity) {
      Toast.makeText(this, "Network security error: " + e.getMessage(),
              Toast.LENGTH_LONG).show();
   } else if (e instanceof Cloudipsp.Exception.ServerInternalError) {
      Toast.makeText(this, "Internal server error: " + e.getMessage(),
              Toast.LENGTH_LONG).show();
   } else if (e instanceof Cloudipsp.Exception.NetworkAccess) {
      Toast.makeText(this, "Network error", Toast.LENGTH_LONG).show();
   } else {
      Toast.makeText(this, "Payment Failed", Toast.LENGTH_LONG).show();
   }
   e.printStackTrace();

   // Handle payment failure (update UI, allow retry, etc.)
}
```

## Additional Configuration Options

### Parameters Reference Table

Below is a comprehensive table of all the parameters available for the Google Pay Button Builder:

| Parameter | Method | Type | Required | Description |
|-----------|--------|------|----------|-------------|
| WebView | `.setWebView()` | `CloudipspWebView` | Yes | The WebView instance that will handle the payment flow |
| Merchant ID | `.setMerchantId()` | `int` | Yes | Your Flitt merchant account ID |
| Token | `.setToken()` | `String` | Yes* | Pre-generated payment token (*required if not using Order) |
| Order | `.setOrder()` | `Order` | Yes* | Payment order details (*required if not using Token) |
| Request Code | `.setRequestCode()` | `int` | Yes | Activity result request code for Google Pay |
| Button Theme | `.setButtonTheme()` | `String` | No | Visual theme - "light" or "dark" |
| Corner Radius | `.setCornerRadius()` | `int` | No | Button corner radius in dp |
| Button Type | `.setButtonType()` | `ButtonType` | No | Button label type - BUY, CHECKOUT, ORDER, SUBSCRIBE, etc. |
| Button Click Listener | `.setOnButtonClickListener()` | `OnButtonClickListener` | No | Custom click handler before Google Pay flow starts |

### Order Parameters

When creating an Order object, you can set the following parameters:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| Amount | `int` | Yes | Payment amount in the smallest currency unit (e.g., cents) |
| Currency | `String` | Yes | Three-letter currency code (e.g., "USD", "EUR", "GEL") |
| Order ID | `String` | Yes | Unique identifier for the order |
| Description | `String` | Yes | Payment description |
| Email | `String` | Yes | Customer email address |

### Customizing the Google Pay Button

The Google Pay button can be customized using various options:

```java
googlePayButton = new GooglePayButton.Builder(this)
// Required settings
    .setWebView(webView)
    .setMerchantId(YOUR_MERCHANT_ID)
    .setToken("YOUR_TOKEN")
    .setRequestCode(RC_GOOGLE_PAY)

// Optional customization
    .setButtonTheme("dark")                // "light" or "dark"
    .setCornerRadius(5)                    // Corner radius in dp
    .setButtonType(GooglePayButtonLayout.ButtonType.BUY) // BUY, CHECKOUT, ORDER, SUBSCRIBE, etc.
    .setOnButtonClickListener(new GooglePayButtonLayout.OnButtonClickListener() {
   @Override
   public void onGooglePayButtonClicked() {
      // Custom click handling before Google Pay flow starts
      Log.d("GooglePay", "Button clicked");
   }
})
        .build();
```

## Card Input & Fee Calculation

### Layout

Add `CardInputView` to your layout — it handles all card fields (number, expiry, CVV) internally:

```xml
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="10dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <EditText
                android:id="@+id/edit_amount"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="number"
                android:maxLength="7" />

            <Spinner
                android:id="@+id/spinner_ccy"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

            <EditText
                android:id="@+id/edit_email"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="textEmailAddress" />

            <com.flitt.android.CardInputView
                android:id="@+id/card_input"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

            <Button
                android:id="@+id/btn_pay_card"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/btn_pay_card" />

        </LinearLayout>
    </ScrollView>

    <com.flitt.android.CloudipspWebView
        android:id="@+id/web_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone" />

</RelativeLayout>
```

### Fee Calculation

Call `setFeeParams()` to enable automatic fee calculation. CVV visibility is handled automatically by `CardInputView` based on the fee response — you do not need to call `setCvvVisible()` manually.

`setFeeParams` has two signatures — token is optional:

```java
// Without token
cardInput.setFeeParams(merchantId, amount, currency, callback);

// With token (optional)
cardInput.setFeeParams(merchantId, amount, currency, token, callback);
```

Example usage in your Activity:

```java
cardInput.setFeeParams(
    4055775,
    amount,
    currency,
    new CardInputView.FeeCallback() {
        @Override
        public void onFeeResult(FeeCalculationResponse response) {
            // CVV visibility is handled automatically inside CardInputView
            // Add any additional UI updates here if needed
        }
    }
);
```

To also handle fee errors, use `FeeCallbackWithError`:

```java
cardInput.setFeeParams(
    4055775,
    amount,
    currency,
    new CardInputView.FeeCallbackWithError() {
        @Override
        public void onFeeResult(FeeCalculationResponse response) {
            // CVV visibility is handled automatically inside CardInputView
        }

        @Override
        public void onFeeError(Exception e) {
            // Handle fee calculation error
        }
    }
);
```

If you don't need to react to the fee response at all, you can pass `null` as the callback:

```java
cardInput.setFeeParams(4055775, amount, currency, null);
```

### Manual Fee Calculation

If you are not using `CardInputView` and want to calculate the fee yourself, you can call `calculateFee` directly on a `Cloudipsp` instance. This must be done on a background thread as it makes a network call.

```java
new Thread(() -> {
    try {
        Cloudipsp cloudipsp = new Cloudipsp(merchantId, this);
        FeeCalculationResponse response = cloudipsp.calculateFee(
                amount,      // int: payment amount
                currency,    // String: e.g. "USD"
                cardBin,     // String: first 6+ digits of card number
                token,       // String: optional, pass null if not available
                merchantId   // Integer: your merchant ID
        );

        runOnUiThread(() -> {
            // Use response fields
        });
    } catch (Exception e) {
        // Handle error
    }
}).start();
```

#### FeeCalculationResponse Fields

| Field | Type | Description |
|-------|------|-------------|
| `discountPercent` | `Double` | Discount percentage applied, or `null` |
| `discountAmount` | `Double` | Discount amount applied, or `null` |
| `feeAmount` | `Double` | Fee amount charged, or `null` |
| `totalAmount` | `Double` | Total amount after fee/discount, or `null` |
| `promoStatus` | `String` | Promo status string, or `null` |
| `message` | `String` | Message from the server, or `null` |
| `cvv2RequirementRaw` | `String` | Raw CVV2 requirement string from server |
| `cvv2Requirement` | `Cvv2Requirement` | Parsed enum: `REQUIRED`, `OPTIONAL`, or `ABSENT` |

#### Cvv2Requirement Enum

| Value | Description |
|-------|-------------|
| `REQUIRED` | CVV is mandatory for this card |
| `OPTIONAL` | CVV is optional for this card |
| `ABSENT` | CVV is not needed — hide the CVV field |

Example handling the response:

```java
FeeCalculationResponse response = cloudipsp.calculateFee(amount, currency, cardBin, null, merchantId);

// Show or hide CVV based on requirement
if (response.cvv2Requirement == FeeCalculationResponse.Cvv2Requirement.ABSENT) {
    cvvEditText.setVisibility(View.GONE);
} else {
    cvvEditText.setVisibility(View.VISIBLE);
}

// Display fee info
String info = "Fee: " + response.feeAmount + "\n"
            + "Total: " + response.totalAmount + "\n"
            + "Discount: " + response.discountAmount;
```

> **Note:** If you are using `CardInputView`, you do not need to call `calculateFee` manually — just call `setFeeParams()` and CVV visibility is handled automatically.

### Card Confirmation

```java
Card card = cardInput.confirm(new CardInputView.ConfirmationErrorHandler() {
    @Override
    public void onCardInputErrorClear(CardInputView view, EditText editText) {}

    @Override
    public void onCardInputErrorCatched(CardInputView view, EditText editText, String error) {
        editText.setError(error);
        editText.requestFocus();
    }
});

if (card != null) {
    // Proceed with payment
}
```

## Sample Project Structure

A typical implementation would have the following structure:

```
com.example.myapplication/
├── MainActivity.java     // Main activity with Google Pay integration
├── res/
│   ├── layout/
│   │   └── activity_gpay.xml   // Layout with WebView and Google Pay button container
│   └── values/
│       ├── strings.xml         // String resources
│       └── colors.xml          // Color definitions
└── AndroidManifest.xml         // App manifest with required permissions
```

## Bank Payment Methods

### Interface Implementation

```java
public class MainActivity extends AppCompatActivity implements Cloudipsp.BankPayCallback {
   // Activity implementation
}
```

### Initialization

```java
// Initialize Cloudipsp with Merchant ID
Cloudipsp cloudipsp = new Cloudipsp(MERCHANT_ID , this);
```

### Interface Methods

The `Cloudipsp.BankPayCallback` interface requires implementing two key methods:

```java
@Override
public void onRedirected(@NonNull BankRedirectDetails bankRedirectDetails) {
   runOnUiThread(() -> {
      // Handle successful payment redirection
      showToast("Payment processed successfully!");

      // Additional details about the payment
      String redirectUrl = bankRedirectDetails.getUrl();
      Log.d(TAG, "Redirect URL: " + redirectUrl);
   });
}

@Override
public void onPaidFailure(@NonNull Cloudipsp.Exception e) {
   runOnUiThread(() -> {
      // Handle payment failure
      showToast("Payment failed: " + e.getMessage());
      Log.e(TAG, "Payment failure", e);
   });
}
```

### Fetching Available Banks

```java
private void fetchBankList() {
   executorService.execute(() -> {
      try {
         // Fetch available banks using payment token
         List<Bank> banks = cloudipsp.getAvailableBankList(CURRENT_TOKEN, this);

         runOnUiThread(() -> {
            if (banks != null && !banks.isEmpty()) {
               // Populate bank list adapter
               BankAdapter adapter = new BankAdapter(this, banks);
               bankListView.setAdapter(adapter);
            } else {
               showToast("No banks available");
            }
         });
      } catch (Exception e) {
         runOnUiThread(() -> {
            Log.e(TAG, "Error getting bank list", e);
            showToast("Error: " + e.getMessage());
         });
      }
   });
}
```

### Payment Initialization

```java
private void initiatePayment(Bank bank) {
   showToast("Processing payment with " + bank.getName());

   executorService.execute(() -> {
      try {
         // Create order with specific details
         Order order = new Order(
                 100,                            // Amount
                 "GEL",                          // Currency
                 "order_" + System.currentTimeMillis(), // Unique order ID
                 "Test Payment",                 // Description
                 "test@gmail.com"                // Customer email
         );

         // Initiate bank payment
         cloudipsp.initiateBankPayment(
                 this,           // Context
                 order,          // Order with payment details
                 bank,           // Selected Bank object
                 this,           // Payment callback interface
                 true            // Automatic redirect flag
         );
      } catch (Exception e) {
         runOnUiThread(() -> {
            Log.e(TAG, "Payment initiation error", e);
            showToast("Payment error: " + e.getMessage());
         });
      }
   });
}
```

### Complete Activity Example

```java
public class MainActivity extends AppCompatActivity implements Cloudipsp.BankPayCallback {
   private static final String TAG = "MainActivity";
   private static final String CURRENT_TOKEN = "your_payment_token";
   private static final int MERCHANT_ID = 1234567;

   private Cloudipsp cloudipsp;
   private ListView bankListView;
   private ExecutorService executorService;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      // Initialize executor service
      executorService = Executors.newFixedThreadPool(2);

      // Initialize Cloudipsp
      cloudipsp = new Cloudipsp(MERCHANT_ID, this);

      // Setup bank list view
      bankListView = findViewById(R.id.bankListView);
      bankListView.setOnItemClickListener((parent, view, position, id) -> {
         Bank selectedBank = (Bank) parent.getItemAtPosition(position);
         initiatePayment(selectedBank);
      });

      // Fetch available banks
      fetchBankList();
   }

   // [Include methods from above: fetchBankList, initiatePayment, 
   //  onRedirected, onPaidFailure, etc.]
}
```

### Callback Method Responsibilities

1. `onRedirected()`:
    - Called when payment is successfully initiated
    - Provides redirect URL details
    - Update UI to reflect successful payment

2. `onPaidFailure()`:
    - Called when payment fails
    - Provides error details
    - Update UI to show payment error

## Troubleshooting

### Common Issues

1. **Google Pay button doesn't appear or is disabled**
    - Ensure Google Pay is available on the device
    - Verify your merchant ID is correct
    - Check that the token is valid

2. **Payment failure with Network Error**
    - Verify internet connection
    - Check that all required permissions are in the manifest

3. **Payment is processed but callback is not triggered**
    - Ensure your activity implements the correct interfaces
    - Verify that you're handling activity results correctly

## Conclusion

The Flitt Android SDK provides a robust solution for integrating payment processing into your Android application. By following this documentation, you should be able to successfully implement Google Pay functionality in your app.

For more information, refer to the official Flitt documentation or contact their support team.