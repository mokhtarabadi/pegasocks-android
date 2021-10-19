package com.mokhtarabadi.pegasocks;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mokhtarabadi.pegasocks.databinding.ActivityEditConfigBinding;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

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
      try (BufferedReader reader =
              new BufferedReader(new InputStreamReader(getAssets().open("config.json")));
          BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
        String line;
        while ((line = reader.readLine()) != null) {
          writer.write(line + "\n");
        }
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
