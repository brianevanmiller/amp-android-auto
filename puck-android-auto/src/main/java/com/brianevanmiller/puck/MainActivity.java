package com.brianevanmiller.puck;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Phone-side setup: sign in to ampcode.com and grant the microphone. Shows the same page
 * the car drives, so you can also call Puck from here.
 */
public class MainActivity extends Activity {
    private FrameLayout webContainer;
    private WebView webView;
    private boolean signingIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.addView(button("Sign in with QR", v -> signInWithQr()),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(button("Reload", v -> PuckCall.get(this).reload()),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        // Keep the page and buttons clear of the status and navigation bars (edge-to-edge on Android 15+).
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });

        setContentView(root);
        webView = PuckCall.get(this).attach(this, webContainer);
        requestPermissionsIfNeeded();
    }

    private Button button(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        return b;
    }

    /**
     * ampcode.com/car signs in by showing a QR code for an already signed-in device to open.
     * That device is this phone, which can't scan its own screen, so read the QR straight
     * off the screen and open its link in the browser (where you're signed in to Amp).
     */
    private void signInWithQr() {
        int[] loc = new int[2];
        webView.getLocationInWindow(loc);
        int w = webView.getWidth(), h = webView.getHeight();
        if (w == 0 || h == 0) return;
        Bitmap shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        PixelCopy.request(getWindow(), new Rect(loc[0], loc[1], loc[0] + w, loc[1] + h), shot, result -> {
            String text = result == PixelCopy.SUCCESS ? decodeQr(shot) : null;
            if (text == null) {
                toast("No QR code on screen. Scroll it into view and try again.");
            } else if (text.startsWith("https://") || text.startsWith("http://")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(text)));
                    signingIn = true;
                    toast("Approve the sign-in, then come back to Puck.");
                } catch (ActivityNotFoundException e) {
                    toast("No browser to open the sign-in link.");
                }
            } else {
                toast("QR code isn't a link: " + text);
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private static String decodeQr(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        try {
            return new QRCodeReader().decode(image, hints).getText();
        } catch (ReaderException e) {
            return null;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void requestPermissionsIfNeeded() {
        List<String> wanted = new ArrayList<>();
        wanted.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 31) wanted.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        wanted.removeIf(p -> checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED);
        if (!wanted.isEmpty()) requestPermissions(wanted.toArray(new String[0]), 1);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from approving the QR sign-in: reload so the page picks up the new session.
        if (signingIn && PuckCall.get(this).state() == PuckCall.State.IDLE) PuckCall.get(this).reload();
        signingIn = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        // Keep the page (and any call) alive; just take it off this screen.
        PuckCall.get(this).detach(webContainer);
        super.onDestroy();
    }
}
