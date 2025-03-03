package com.cloudipsp.android;

/**
 * Created by vberegovoy on 09.11.15.
 */
public enum Currency {
    AMD,
    AZN,
    EUR,
    GEL,
    KZT,
    MDL,
    USD,
    UZS;

    @Override
    public String toString() {
        return name();
    }


}
