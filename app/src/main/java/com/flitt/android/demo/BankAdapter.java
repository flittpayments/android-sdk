package com.flitt.android.demo;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.flitt.android.Bank;

import java.util.List;

public class BankAdapter extends ArrayAdapter<Bank> {

    public BankAdapter(Context context, List<Bank> banks) {
        super(context, 0, banks);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Bank bank = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.bank_item, parent, false);
        }

        TextView bankName = convertView.findViewById(R.id.bankName);
        TextView bankCountry = convertView.findViewById(R.id.bankCountry);
        LinearLayout itemContainer = convertView.findViewById(R.id.itemContainer);

        // Set the bank information
        bankName.setText(bank.getName());
        bankCountry.setText(bank.getCountry());

        // Alternate background colors for better separation
        if (position % 2 == 0) {
            itemContainer.setBackgroundColor(Color.parseColor("#F5F5F5"));
        } else {
            itemContainer.setBackgroundColor(Color.WHITE);
        }

        return convertView;
    }
}