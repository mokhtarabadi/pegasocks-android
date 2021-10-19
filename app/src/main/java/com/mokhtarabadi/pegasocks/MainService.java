package com.mokhtarabadi.pegasocks;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;

public class MainService extends VpnService {

  public static final String ACTION_START = "pegas_sample_start";
  public static final String ACTION_STOP = "pegas_sample_stop";
  private static final String TAG = "vpn_service";
  private static final String PRIVATE_VLAN4_CLIENT = "10.0.0.1";
  private static final String PRIVATE_VLAN4_ROUTER = "10.0.0.2";

  private static final String PRIVATE_VLAN6_CLIENT = "fc00::1";
  private static final String PRIVATE_VLAN6_ROUTER = "fc00::2";

  private static final String PRIVATE_NETMASK = "255.255.255.252";

  private static final int PRIVATE_MTU = 1500;
  private static volatile boolean isTun2SocksRunning = false;
  private ConnectivityManager connectivityManager;
  private Notification notification;
  private ParcelFileDescriptor descriptor;
  private Thread tun2SocksThread;
  private Thread pegaSocksThread;

  private Thread protectorThread;
  private volatile ServerSocket serverSocket;

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
  private NetworkConnectivityMonitor networkConnectivityMonitor;

  private boolean networkConnectivityMonitorStarted = false;

  @Override
  public void onCreate() {
    super.onCreate();

    MainNative.initialize(getApplicationContext()); // load native libraries...

    createNotification();

    connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      networkConnectivityMonitor = new NetworkConnectivityMonitor();
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && intent.getAction().equals(ACTION_STOP)) {
      stopService();
      return START_NOT_STICKY;
    }
    startService();
    return START_STICKY;
  }

  @Override
  public void onRevoke() {
    stopService();
  }

  @Override
  public void onLowMemory() {
    stopService();
    super.onLowMemory();
  }

  @Override
  public void onDestroy() {
    stopForeground(true);

    super.onDestroy();
  }

  private void createNotification() {
    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

    NotificationChannelCompat notificationChannel =
        new NotificationChannelCompat.Builder(
                "vpn_service", NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Vpn service")
            .build();
    notificationManager.createNotificationChannel(notificationChannel);

    Intent stopIntent = new Intent(this, MainService.class);
    stopIntent.setAction(ACTION_STOP);

    PendingIntent stopPendingIntent;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      stopPendingIntent = PendingIntent.getForegroundService(this, 1, stopIntent, 0);
    } else {
      stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, 0);
    }

    PendingIntent contentPendingIntent =
        PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), 0);

    notification =
        new NotificationCompat.Builder(this, notificationChannel.getId())
            .setContentTitle("Vpn service")
            .setContentText("Testing Pegas")
            .setSmallIcon(R.drawable.ic_baseline_vpn_lock_24)
            .addAction(R.drawable.ic_baseline_stop_24, "Stop", stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)
            .setContentIntent(contentPendingIntent)
            .build();
  }

  private void startService() {
    if (isTun2SocksRunning) {
      Log.w(TAG, "seems vpn already running!?");
      return;
    }

    startForeground(1, notification);

    boolean supportIPv4 = true;
    boolean supportIPv6 = false;

    Builder builder = new Builder().setSession("Pegas").setMtu(PRIVATE_MTU);

    if (supportIPv4) {
      builder.addAddress(PRIVATE_VLAN4_CLIENT, 30).addRoute("0.0.0.0", 0);
      for (String iPv4DNSServer : new HashSet<>(Arrays.asList("1.0.0.1", "1.1.1.1"))) {
        builder.addDnsServer(iPv4DNSServer);
      }
    }

    if (supportIPv6) {
      builder.addAddress(PRIVATE_VLAN6_CLIENT, 126).addRoute("::", 0);
      for (String iPv6DNSServer :
          new HashSet<>(Arrays.asList("2606:4700:4700::1001", "2606:4700:4700::1111"))) {
        builder.addDnsServer(iPv6DNSServer);
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      builder.setMetered(false);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Network activeNetwork = connectivityManager.getActiveNetwork();
      if (activeNetwork != null) {
        builder.setUnderlyingNetworks(new Network[] {activeNetwork});
      }
    }

    descriptor = builder.establish();
    if (descriptor == null) {
      stopSelf(); // !?
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      startNetworkConnectivityMonitor();
    }

    protectorThread =
        new Thread(
            () -> {
              try {
                serverSocket = new ServerSocket(9091);
                ByteBuffer buffer = ByteBuffer.allocate(4);

                while (true) {
                  Socket socket = serverSocket.accept();

                  buffer.clear();
                  int size = socket.getInputStream().read(buffer.array());
                  if (size == 4) {
                    int fd = buffer.getInt();
                    buffer.clear();

                    if (protect(fd)) {
                      buffer.putInt(fd);
                    } else {
                      buffer.putInt(-1);
                    }

                  } else {
                    buffer.putInt(-1);
                  }

                  socket.getOutputStream().write(buffer.array());
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
    protectorThread.start();

    pegaSocksThread =
        new Thread(
            () -> {
              File configFile = new File(getFilesDir(), "pegas.json");
              boolean result = MainNative.startPegaSocks(configFile.getAbsolutePath(), 1);
              Log.d(TAG, "pegas stopped, result: " + result);
            });

    pegaSocksThread.start();

    tun2SocksThread =
        new Thread(
            () -> {
              isTun2SocksRunning = true;

              boolean result =
                  MainNative.startTun2Socks(
                      descriptor,
                      PRIVATE_MTU,
                      "127.0.0.1",
                      1080,
                      PRIVATE_VLAN4_ROUTER,
                      PRIVATE_VLAN6_ROUTER,
                      PRIVATE_NETMASK,
                      true);
              Log.d(TAG, "tun2socks stopped, result: " + result);

              isTun2SocksRunning = false;
            });
    tun2SocksThread.start();
  }

  private void stopService() {
    if (!isTun2SocksRunning) {
      Log.w(TAG, "seems already stopped!?");
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      stopNetworkConnectivityMonitor();
    }

    MainNative.stopTun2Socks();
    try {
      tun2SocksThread.join();
      descriptor.close();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
    descriptor = null;

    MainNative.stopPegaSocks();
    try {
      pegaSocksThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    try {
      serverSocket.close();
      protectorThread.join();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
    serverSocket = null;

    stopSelf();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
  private void startNetworkConnectivityMonitor() {
    NetworkRequest.Builder builder =
        new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) { // workarounds for OEM bugs
      builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
      builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
    }
    NetworkRequest request = builder.build();

    try {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        connectivityManager.registerNetworkCallback(request, networkConnectivityMonitor);
      } else {
        connectivityManager.requestNetwork(request, networkConnectivityMonitor);
      }
      networkConnectivityMonitorStarted = true;
    } catch (SecurityException se) {
      se.printStackTrace();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
  private void stopNetworkConnectivityMonitor() {
    try {
      if (networkConnectivityMonitorStarted) {
        connectivityManager.unregisterNetworkCallback(networkConnectivityMonitor);
        networkConnectivityMonitorStarted = false;
      }
    } catch (Exception e) {
      // Ignore, monitor not installed if the connectivity checks failed.
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
  private class NetworkConnectivityMonitor extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(@NonNull Network network) {
      setUnderlyingNetworks(new Network[] {network});
    }

    @Override
    public void onCapabilitiesChanged(
        @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
      setUnderlyingNetworks(new Network[] {network});
    }

    @Override
    public void onLost(@NonNull Network network) {
      setUnderlyingNetworks(null);
    }
  }
}
