package com.tomer.poke.notifier.plus;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.tomer.poke.notifier.R;
import com.tomer.poke.notifier.plus.Activities.ChromeTabActivity;
import com.tomer.poke.notifier.plus.Activities.MainActivity;
import com.tomer.poke.notifier.plus.Receivers.ScreenReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class MainService extends Service implements PokemonGOListener {
    private Prefs prefs;
    private PowerManager.WakeLock wl;
    private boolean isGoOpen = false;
    private WindowManager.LayoutParams windowParams;
    private ScreenReceiver screenReceiver;
    private IntentFilter filter;
    private int originalBrightness;
    private int originalLocationMode;
    private int originalBrightnessMode;
    private FloatingActionMenu fab;
    private PowerManager.WakeLock proximityToTurnOff;
    private WindowManager.LayoutParams floatingActionMenuLP;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(MainService.class.getSimpleName(), "Main service started");
        prefs = new Prefs(this);
        initWindowManager();
        initOriginalStates();
        initAccelerometer();
        initScreenHolder();
        initScreenReceiver();
        initFloatingActionButton();
        createPersistentNotification();
        updateCurrentApp();
    }

    private void initFloatingActionButton() {
        try {
            int theme = prefs.getInt(Prefs.theme, 0);
            int color = theme == 1 ? R.color.colorPrimaryBlue : (theme == 2 ? R.color.colorPrimaryRed : (theme == 3 ? R.color.colorPrimaryYellow : R.color.colorPrimary));
            fab = (FloatingActionMenu) LayoutInflater.from(this).inflate(R.layout.fab, null).findViewById(R.id.menu);
            ((FloatingActionMenu) fab.findViewById(R.id.menu)).setMenuButtonColorNormal(ContextCompat.getColor(this, color));
            fab.findViewById(R.id.menu).setAlpha(0.8f);
            ((FloatingActionMenu) fab.findViewById(R.id.menu)).setOnMenuToggleListener(new FloatingActionMenu.OnMenuToggleListener() {
                @Override
                public void onMenuToggle(boolean opened) {
                    fab.findViewById(R.id.menu).setAlpha(fab.findViewById(R.id.menu).getAlpha() == 0.8f ? 1 : 0.8f);
                }
            });

            ((FloatingActionButton) fab.findViewById(R.id.pokevision)).setColorNormal(ContextCompat.getColor(this, color));
            ((FloatingActionButton) fab.findViewById(R.id.pidgey_calc)).setColorNormal(ContextCompat.getColor(this, color));
            ((FloatingActionButton) fab.findViewById(R.id.lock_fab)).setColorNormal(ContextCompat.getColor(this, color));
            ((FloatingActionButton) fab.findViewById(R.id.pokedex)).setColorNormal(ContextCompat.getColor(this, color));
            ((FloatingActionButton) fab.findViewById(R.id.settings)).setColorNormal(ContextCompat.getColor(this, color));
            ((FloatingActionButton) fab.findViewById(R.id.silph_road)).setColorNormal(ContextCompat.getColor(this, color));
            ((FloatingActionButton) fab.findViewById(R.id.pogotoolkit)).setColorNormal(ContextCompat.getColor(this, color));
            floatingActionMenuLP = new WindowManager.LayoutParams(100, 100, WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT);
            fab.findViewById(R.id.pokevision).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    loadChromeTabFromURL("https://pokevision.com/");
                }
            });
            fab.findViewById(R.id.pidgey_calc).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    loadChromeTabFromURL("http://www.pidgeycalc.com/");
                }
            });
            fab.findViewById(R.id.pokedex).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    loadChromeTabFromURL("http://www.pokemon.com/us/pokedex/");
                }
            });
            fab.findViewById(R.id.pogotoolkit).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    loadChromeTabFromURL("http://pogotoolkit.com/");
                }
            });
            fab.findViewById(R.id.silph_road).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    loadChromeTabFromURL("https://thesilphroad.com/");
                }
            });
            fab.findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(getApplicationContext(), MainActivity.class));
                }
            });
            fab.findViewById(R.id.lock_fab).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    darkenTheScreen(true);
                }
            });
            if (!prefs.getBoolean(Prefs.overlay, false)) {
                ((FloatingActionMenu) fab.findViewById(R.id.menu)).removeMenuButton((FloatingActionButton) fab.findViewById(R.id.lock_fab));
            }
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateFab() {
        floatingActionMenuLP.gravity = Integer.parseInt(prefs.getString(Prefs.fab_position, "51"));
        Set<String> selections = prefs.getStringSet("buttons");
        ArrayList<Integer> ids = new ArrayList<>();
        if (selections != null) {
            for (String key : selections) {
                if (key.equals("pidgey_calc"))
                    ids.add(R.id.pidgey_calc);

                if (key.equals("pogotoolkit"))
                    ids.add(R.id.pogotoolkit);

                if (key.equals("pokevision"))
                    ids.add(R.id.pokevision);

                if (key.equals("pokedex"))
                    ids.add(R.id.pokedex);

                if (key.equals("the_silph_road"))
                    ids.add(R.id.silph_road);
            }
            int[] buttons = {R.id.pidgey_calc, R.id.pogotoolkit, R.id.pokevision, R.id.pokedex, R.id.silph_road};
            for (int button : buttons) {
                if (!ids.contains(button)) {
                    if (fab.findViewById(button) != null)
                        fab.removeMenuButton((FloatingActionButton) fab.findViewById(button));
                }
            }
        }
    }

    private void loadChromeTabFromURL(String url) {
        Globals.url = url;
        Intent intent = new Intent(getApplicationContext(), ChromeTabActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showFAB(boolean state) {
        if (state) {
            if (fab == null)
                initFloatingActionButton();
            updateFab();
            if (fab.getWindowToken() == null) {
                ((WindowManager) this.getSystemService(WINDOW_SERVICE)).addView(fab, floatingActionMenuLP);
            }
        } else if (fab.getWindowToken() != null) {
            fab.close(true);
            ((WindowManager) this.getSystemService(WINDOW_SERVICE)).removeView(fab);
            fab.invalidate();
        }
    }

    private void initOriginalStates() {
        originalBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 80);
        originalBrightnessMode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
    }

    private void createPersistentNotification() {
        if (prefs.getBoolean(Prefs.persistent_notification, true)) {
            Notification.Builder builder = new Notification.Builder(getApplicationContext());
            builder.setContentTitle("Enhancements for go is running");
            builder.setOngoing(true);
            builder.setPriority(Notification.PRIORITY_MIN);
            builder.setSmallIcon(android.R.color.transparent);
            Notification notification = builder.build();
            NotificationManager notificationManger = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManger.notify(33, notification);
        }
    }

    private void initScreenHolder() {
        wl = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.FULL_WAKE_LOCK, "Tweaks For GO Tag");
    }

    private void initScreenReceiver() {
        filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        screenReceiver = new ScreenReceiver();
    }

    private void unregisterScreenReceiver() {
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }
    }

    private void initWindowManager() {
        if (Globals.windowManager == null)
            Globals.windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowParams = new WindowManager.LayoutParams(-1, -1, 2003, 65794, -2);
        windowParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
    }

    private void initAccelerometer() {
        if (prefs.getBoolean(Prefs.overlay, false) || prefs.getBoolean(Prefs.dim, false)) {
            if (Globals.black == null)
                Globals.black = new LinearLayout(getApplicationContext());
            Globals.black.setOnTouchListener(new View.OnTouchListener() {
                private GestureDetector gestureDetector = new GestureDetector(MainService.this, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        darkenTheScreen(false);
                        return super.onDoubleTap(e);
                    }
                });

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d("TEST", "Raw event: " + event.getAction() + ", (" + event.getRawX() + ", " + event.getRawY() + ")");
                    gestureDetector.onTouchEvent(event);
                    return true;
                }
            });
            Globals.black.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Globals.black.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), android.R.color.black));
            Globals.blackLayout = Globals.black;
        }
    }

    private void setBatterySaver(boolean status) {
        try {
            if (!isConnected()) {
                Settings.Global.putInt(getContentResolver(), "low_power", status ? 1 : 0);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "settings put global low_power " + (status ? 1 : 0)});
                process.waitFor();
            } catch (InterruptedException | IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void killBackgroundProcesses() {
        Log.d(MainService.class.getSimpleName(), "Killing background processes");
        List<ApplicationInfo> packages = getPackageManager().getInstalledApplications(0);
        ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ApplicationInfo packageInfo : packages) {
            if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1) continue;
            if (packageInfo.packageName.contains("com.tomer")) continue;
            mActivityManager.killBackgroundProcesses(packageInfo.packageName);
        }
    }

    private void extremeBatterySaver(boolean state) {
        try {
            if (state) {
                Settings.Secure.putInt(getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
                Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer", 0);
            } else {
                Settings.Secure.putInt(getContentResolver(), Settings.Secure.LOCATION_MODE, originalLocationMode);
                Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer", -1);
            }
        } catch (Exception ignored) {
            prefs.set(Prefs.extreme_battery_saver, false);
        }
    }

    private void maximizeBrightness(boolean state) {
        Log.d(MainService.class.getSimpleName(), "Changing screen brightness");
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, state ? Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL : originalBrightnessMode);
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, state ? 255 : originalBrightness);
    }

    private void darkenTheScreen(boolean state) {
        if (state) {
            try {
                Globals.windowManager.removeView(Globals.black);
            } catch (Exception ignored) {
                Log.d("Receiver", "View is not attached");
            }
            Globals.windowManager.addView(Globals.black, windowParams);
            registerReceiver(screenReceiver, filter);
            final TextView doubleTapToDismiss = new TextView(this);
            doubleTapToDismiss.setText("Double tap to dismiss");
            doubleTapToDismiss.setTextColor(Color.WHITE);
            doubleTapToDismiss.setGravity(View.TEXT_ALIGNMENT_CENTER);
            doubleTapToDismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
            Globals.windowManager.addView(doubleTapToDismiss, windowParams);
            dimScreen(true);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Globals.windowManager.removeView(doubleTapToDismiss);

                }
            }, 3000);
        } else {
            try {
                Globals.windowManager.removeView(Globals.black);
                unregisterScreenReceiver();
                dimScreen(false);
            } catch (Exception ignored) {
                Log.d("Receiver", "View is not attached");
            }
        }
    }

    private void initProximityToLock(boolean state) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && prefs.getBoolean(Prefs.screen_of_proximity, true)) {
            if (proximityToTurnOff == null)
                proximityToTurnOff = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "proximity to lock");
            if (state)
                proximityToTurnOff.acquire();
            else if (proximityToTurnOff.isHeld())
                proximityToTurnOff.release();
        }
    }

    private void dimScreen(boolean state) {
        Log.d("Original brightness is ", String.valueOf(originalBrightness));
        if (!state && prefs.getBoolean(Prefs.maximize_brightness, false)) {
            maximizeBrightness(true);
            return;
        }
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, state ? Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL : originalBrightnessMode);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, state ? 0 : originalBrightness);
        } catch (SecurityException e) {
            Log.d(MainService.class.getSimpleName(), "Lacking modify settings permission");
        }
    }

    private void setNotification(boolean state) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.tomer.poke.notifier", "com.tomer.poke.notifier.Services.MainService"));
            if (state)
                startService(i);
            else
                stopService(i);
        } catch (Exception ignored) {
            Log.d(MainService.class.getSimpleName(), "Notifications for GO is not installed");
        }
    }

    private boolean isConnected() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        assert intent != null;
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        onStop();
    }

    private void updateCurrentApp() {
        if (isGoRunning() && !isGoOpen) {
            isGoOpen = true;
            onStart();
        } else if (!isGoRunning() && isGoOpen) {
            isGoOpen = false;
            onStop();
        }
        Log.d(MainService.class.getSimpleName(), "GO is " + (isGoOpen ? "" : "not ") + "running");
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                updateCurrentApp();
            }
        }, REFRESH_INTERVAL);
    }

    @Override
    public void onStart() {
        Log.d(MainService.class.getSimpleName(), "GO launched");
        if (prefs.getBoolean(Prefs.batterySaver, false))
            setBatterySaver(true);
        if (prefs.getBoolean(Prefs.keepAwake, true))
            wl.acquire();
        if (prefs.getBoolean(Prefs.kill_background_processes, false))
            killBackgroundProcesses();
        if (prefs.getBoolean(Prefs.extreme_battery_saver, false)) {
            Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer_enabled", 1);
            originalLocationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, 0);
            extremeBatterySaver(true);
        }
        if (prefs.getBoolean(Prefs.maximize_brightness, false))
            maximizeBrightness(true);
        if (prefs.getBoolean(Prefs.showFAB, false))
            showFAB(true);
        initProximityToLock(true);

        setNotification(true);
        isGoOpen = true;
    }

    @Override
    public void onStop() {
        Log.d(MainService.class.getSimpleName(), "GO closed");
        if (prefs.getBoolean(Prefs.batterySaver, false))
            setBatterySaver(false);
        if (wl.isHeld())
            wl.release();
        if (prefs.getBoolean(Prefs.extreme_battery_saver, false))
            extremeBatterySaver(false);
        if (prefs.getBoolean(Prefs.maximize_brightness, false))
            maximizeBrightness(false);
        if (prefs.getBoolean(Prefs.showFAB, false))
            showFAB(false);

        initProximityToLock(false);

        setNotification(false);
        isGoOpen = false;
    }

    @Override
    public boolean isGoRunning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - REFRESH_INTERVAL * REFRESH_INTERVAL, time);
            if (appList != null && appList.size() > 0) {
                SortedMap<Long, UsageStats> mySortedMap = new TreeMap<>();
                for (UsageStats usageStats : appList) {
                    mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                }
                if (!mySortedMap.isEmpty()) {
                    String currentAppName = mySortedMap.get(mySortedMap.lastKey()).getPackageName();
                    return (currentAppName.equals("com.android.systemui") || currentAppName.equals("com.tomer.poke.notifier") || currentAppName.equals(getPackageName())) ? isGoOpen : currentAppName.equals(Constants.GOPackageName);
                }
            }
        } else {
            ActivityManager am = (ActivityManager) getBaseContext().getSystemService(ACTIVITY_SERVICE);
            return am.getRunningTasks(1).get(0).topActivity.getPackageName().equals(Constants.GOPackageName);
        }
        return isGoOpen;
    }
}
