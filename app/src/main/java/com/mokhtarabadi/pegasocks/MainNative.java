package com.mokhtarabadi.pegasocks;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.getkeepsafe.relinker.ReLinker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class MainNative {

  private static final String TAG = "native";
  private static volatile boolean isInitialized = false;

  public static void initialize(Context context) {
    if (isInitialized) {
      Log.w(TAG, "initialization before done");
      return;
    }

    ReLinker.log(message -> Log.d(TAG, message))
        .recursively()
        .loadLibrary(
            context,
            "native-lib",
            new ReLinker.LoadListener() {
              @Override
              public void success() {
                isInitialized = true;
              }

              @Override
              public void failure(Throwable t) {
                isInitialized = false;
                Log.e(TAG, "failed to load native libraries", t);
              }
            });
  }

  public static boolean startTun2Socks(
      ParcelFileDescriptor vpnInterfaceFileDescriptor,
      int vpnInterfaceMtu,
      String socksServerAddress,
      int socksServerPort,
      String netIPv4Address,
      @Nullable String netIPv6Address,
      String netmask,
      boolean forwardUdp) {
    ArrayList<String> arguments = new ArrayList<>();
    arguments.add("badvpn-tun2socks");
    arguments.addAll(Arrays.asList("--logger", "stdout"));
    arguments.addAll(Arrays.asList("--loglevel", "info"));
    arguments.addAll(Arrays.asList("--tunfd", String.valueOf(vpnInterfaceFileDescriptor.getFd())));
    arguments.addAll(Arrays.asList("--tunmtu", String.valueOf(vpnInterfaceMtu)));
    arguments.addAll(Arrays.asList("--netif-ipaddr", netIPv4Address));

    if (!TextUtils.isEmpty(netIPv6Address)) {
      arguments.addAll(Arrays.asList("--netif-ip6addr", netIPv6Address));
    }

    arguments.addAll(Arrays.asList("--netif-netmask", netmask));
    arguments.addAll(
        Arrays.asList(
            "--socks-server-addr",
            String.format(Locale.US, "%s:%d", socksServerAddress, socksServerPort)));

    if (forwardUdp) {
      arguments.add("--socks5-udp");
    }

    int exitCode = start_tun2socks(arguments.toArray(new String[] {}));
    return exitCode == 0;
  }

  private static native int start_tun2socks(String[] args);

  public static native void stopTun2Socks();

  public static native void printTun2SocksHelp();

  public static native void printTun2SocksVersion();

  public static native boolean startPegaSocks(String configPath, int threads);

  public static native void stopPegaSocks();
}
