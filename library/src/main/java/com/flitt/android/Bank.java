package com.flitt.android;


public class Bank {

    private String bankId;
    private int countryPriority;
    private int userPriority;
    private boolean quickMethod;
    private boolean userPopular;
    private String name;
    private String country;
    private String bankLogo;
    private String alias;

    public Bank(String bankId, int countryPriority, int userPriority, boolean quickMethod, boolean userPopular, String name, String country, String bankLogo, String alias) {
        this.bankId = bankId;
        this.countryPriority = countryPriority;
        this.userPriority = userPriority;
        this.quickMethod = quickMethod;
        this.userPopular = userPopular;
        this.name = name;
        this.country = country;
        this.bankLogo = bankLogo;
        this.alias = alias;
    }


    // Add these getter methods to your Bank class
    public String getBankId() {
        return bankId;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getBankLogo() {
        return bankLogo;
    }

    public String getAlias() {
        return alias;
    }

    public int getCountryPriority() {
        return countryPriority;
    }

    public int getUserPriority() {
        return userPriority;
    }

    public boolean isQuickMethod() {
        return quickMethod;
    }

    public boolean isUserPopular() {
        return userPopular;
    }
}
