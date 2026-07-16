package com.tickets.canjeapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.tickets.canjeapp.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new ScannerFragment())
                    .commit();
        }
    }
}
