package com.fongmi.android.tv.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.util.UriUtil;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.server.Server;
import com.google.common.net.HttpHeaders;
import com.permissionx.guolindev.PermissionX;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Map;

public class Utils {

    public static boolean isEnterKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_SPACE || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    public static boolean isUpKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP || event.getKeyCode() == KeyEvent.KEYCODE_PAGE_UP || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    public static boolean isDownKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_PAGE_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT;
    }

    public static boolean isLeftKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    public static boolean isRightKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    public static boolean isBackKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_BACK;
    }

    public static boolean isDigitKey(KeyEvent event) {
        return event.getKeyCode() >= KeyEvent.KEYCODE_0 && event.getKeyCode() <= KeyEvent.KEYCODE_9 || event.getKeyCode() >= KeyEvent.KEYCODE_NUMPAD_0 && event.getKeyCode() <= KeyEvent.KEYCODE_NUMPAD_9;
    }

    public static boolean isMenuKey(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_MENU;
    }

    public static void toggleFullscreen(Activity activity, boolean fullscreen) {
        if (fullscreen) Utils.hideSystemUI(activity);
        else Utils.showSystemUI(activity);
    }

    public static void showSystemUI(Activity activity) {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    public static void hideSystemUI(Activity activity) {
        hideSystemUI(activity.getWindow());
    }

    public static void hideSystemUI(Window window) {
        int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    public static boolean isAutoRotate() {
        return Settings.System.getInt(App.get().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    public static boolean hasPermission(FragmentActivity activity) {
        return PermissionX.isGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static Map<String, String> checkHeaders(Map<String, String> headers) {
        if (Prefers.getUa().isEmpty() || headers.containsKey(HttpHeaders.USER_AGENT) || headers.containsKey(HttpHeaders.USER_AGENT.toLowerCase())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Prefers.getUa());
        return headers;
    }

    public static String checkClan(String text) {
        if (text.contains("/localhost/")) text = text.replace("/localhost/", "/");
        if (text.startsWith("clan")) text = text.replace("clan", "file");
        return text;
    }

    public static String convert(String baseUrl, String text) {
        if (TextUtils.isEmpty(text)) return "";
        if (text.startsWith("clan")) return checkClan(text);
        return UriUtil.resolve(baseUrl, text);
    }

    public static String convert(String url) {
        Uri uri = Uri.parse(url);
        if ("file".equals(uri.getScheme())) return Server.get().getAddress(url);
        if ("local".equals(uri.getScheme())) return Server.get().getAddress(uri.getHost());
        if ("proxy".equals(uri.getScheme())) return url.replace("proxy://", Server.get().getAddress("proxy?"));
        return url;
    }

    public static String getMd5(String src) {
        try {
            if (TextUtils.isEmpty(src)) return "";
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(src.getBytes());
            BigInteger no = new BigInteger(1, bytes);
            StringBuilder sb = new StringBuilder(no.toString(16));
            while (sb.length() < 32) sb.insert(0, "0");
            return sb.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    public static String getDeviceId() {
        return Settings.Secure.getString(App.get().getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static String getDeviceName() {
        String model = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        return model.startsWith(manufacturer) ? model : manufacturer + " " + model;
    }

    public static String getBase64(String ext) {
        return Base64.encodeToString(ext.getBytes(), Base64.DEFAULT | Base64.NO_WRAP);
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }

    public static CharSequence getClipText() {
        return ((ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE)).getText();
    }

    public static long format(SimpleDateFormat format, String src) {
        try {
            return format.parse(src).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getDigit(String text) {
        try {
            if (text.startsWith("上") || text.startsWith("下")) return -1;
            if (text.contains(".")) text = text.substring(0, text.lastIndexOf("."));
            if (text.startsWith("4k")) text = text.replace("4k", "");
            return Integer.parseInt(text.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        IBinder windowToken = view.getWindowToken();
        if (imm != null && windowToken != null) {
            imm.hideSoftInputFromWindow(windowToken, 0);
        }
    }
}
