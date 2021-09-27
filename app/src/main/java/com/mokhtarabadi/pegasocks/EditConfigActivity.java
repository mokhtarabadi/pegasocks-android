package com.mokhtarabadi.pegasocks;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mokhtarabadi.pegasocks.databinding.ActivityEditConfigBinding;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class EditConfigActivity extends AppCompatActivity {

    private ActivityEditConfigBinding binding;

    private File configFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditConfigBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configFile = new File(getFilesDir(), "pegas.json");

        if (!configFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                writer.write("{\n" +
                        "  \"servers\": [\n" +
                        "    {\n" +
                        "      \"server_type\": \"v2ray\",\n" +
                        "      \"server_address\": \"172.67.201.58\",\n" +
                        "      \"server_port\": 443,\n" +
                        "      \"password\": \"6f561f3b-bae5-4186-9481-f6812458a655\",\n" +
                        "      \"secure\": \"aes-128-gcm\",\n" +
                        "      \"ssl\": {\n" +
                        "        \"sni\": \"6803960e2f4046a0be8168afbd7a0b78.hypervpn.org\"\n" +
                        "      },\n" +
                        "      \"websocket\": {\n" +
                        "        \"path\": \"/5hrckwzt\",\n" +
                        "        \"hostname\": \"6803960e2f4046a0be8168afbd7a0b78.hypervpn.org\"\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"local_address\": \"0.0.0.0\",\n" +
                        "  \"local_port\": 1080,\n" +
                        "  \"log_level\": 1\n" +
                        "}\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            runOnUiThread(() -> binding.config.setText(sb.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write(binding.config.getText().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }
}
