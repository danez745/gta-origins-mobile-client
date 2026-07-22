package com.touch.mobile.dark;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Toast;

import com.wardrumstudios.utils.WarMedia;

import com.touch.mobile.dark.gui.util.Utils;

public class MainGTA extends WarMedia {
    public static MainGTA self = null;
    static String vmVersion;
    private boolean once = false;
    private static boolean nativeLibrariesLoaded = true;
    private static String nativeLoadError = null;

    static {
        vmVersion = null;
        System.out.println("**** Loading SO's");
        try {
            vmVersion = System.getProperty("java.vm.version");
            System.out.println("vmVersion " + vmVersion);
            if (BuildConfig.LOAD_IMM_EMULATOR) {
                System.loadLibrary("ImmEmulatorJ");
            } else {
                System.out.println("Skipping ImmEmulatorJ load");
            }
            System.loadLibrary("GTASA");
            System.loadLibrary("samp");
            nativeLibrariesLoaded = true;
        } catch (Throwable t) {
            nativeLibrariesLoaded = false;
            nativeLoadError = t.getClass().getSimpleName() + ": " + t.getMessage();
            System.out.println("Native library load failed: " + nativeLoadError);
        }
    }

    public boolean ServiceAppCommand(String str, String str2) {
        return false;
    }
    public int ServiceAppCommandValue(String str, String str2) {
        return 0;
    }

    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
    }

    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
    }

    public void onCreate(Bundle bundle) {
        if(!once) {
            once = true;
        }

        System.out.println("MainGTA onCreate");
        self = this;
        wantsMultitouch = true;
        wantsAccelerometer = true;
        if (!nativeLibrariesLoaded) {
            String message = nativeLoadError != null ? nativeLoadError : "Native libraries failed to load";
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> Toast.makeText(MainGTA.this, message, Toast.LENGTH_LONG).show());
            finish();
            return;
        }
        super.onCreate(bundle);
        Utils.currentContext = this;
    }

    public void onDestroy() {
        System.out.println("MainGTA onDestroy");
        super.onDestroy();
    }

    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        return super.onKeyDown(i, keyEvent);
    }

    public void onPause() {
        System.out.println("MainGTA onPause");
        super.onPause();
    }

    public void onRestart() {
        System.out.println("MainGTA onRestart");
        super.onRestart();
    }

    public void onResume() {
        System.out.println("MainGTA onResume");
        super.onResume();
    }

    public void onStart() {
        System.out.println("MainGTA onStart");
        super.onStart();
    }

    public void onStop() {
        System.out.println("MainGTA onStop");
        super.onStop();
    }
}