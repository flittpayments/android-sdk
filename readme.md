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
10. [Sample Project Structure](#sample-project-structure)

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
    implementation 'com.cloudipsp:android-sdk:x.x.x' // Replace with the latest version
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

   <com.cloudipsp.android.CloudipspWebView
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
