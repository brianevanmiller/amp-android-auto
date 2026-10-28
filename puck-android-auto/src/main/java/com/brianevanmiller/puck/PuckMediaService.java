package com.brianevanmiller.puck;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;

import java.util.Collections;
import java.util.List;

/**
 * Puck's car UI. Android Auto only lists sideloaded apps of a few types (template apps
 * aren't one of them), so Puck presents itself as a media app: a single "Call Puck"
 * item, where play starts the call and pause / stop hangs up. Android Auto draws the
 * media screens itself, so they stay usable while driving.
 */
public class PuckMediaService extends MediaBrowserService implements PuckCall.Listener {
    private static final String ROOT = "root";
    private static final String CALL_ID = "call";
    // Android Auto may send "play" to the last media app when the phone connects.
    // Don't let that place a call nobody asked for.
    private static final long CONNECT_AUTOPLAY_GRACE_MS = 5000;

    private MediaSession session;
    private PuckCall call;
    private Bitmap icon;
    private long createdAt;

    @Override
    public void onCreate() {
        super.onCreate();
        createdAt = SystemClock.elapsedRealtime();
        call = PuckCall.get(this);
        icon = renderIcon();

        session = new MediaSession(this, "Puck");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (SystemClock.elapsedRealtime() - createdAt < CONNECT_AUTOPLAY_GRACE_MS) return;
                startCall();
            }

            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                startCall();
            }

            @Override
            public void onPlayFromSearch(String query, Bundle extras) {
                // "Hey Google, play Puck."
                startCall();
            }

            @Override
            public void onPause() {
                call.hangUp();
            }

            @Override
            public void onStop() {
                call.hangUp();
            }
        });
        session.setActive(true);
        setSessionToken(session.getSessionToken());

        call.addListener(this);
        onPuckCallChanged();
    }

    @Override
    public void onDestroy() {
        call.removeListener(this);
        session.release();
        super.onDestroy();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot(ROOT, null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        if (!ROOT.equals(parentId)) {
            result.sendResult(Collections.emptyList());
            return;
        }
        MediaDescription item = new MediaDescription.Builder()
                .setMediaId(CALL_ID)
                .setTitle("Call Puck")
                .setSubtitle("Talk to Amp's agent")
                .setIconBitmap(icon)
                .build();
        result.sendResult(Collections.singletonList(
                new MediaBrowser.MediaItem(item, MediaBrowser.MediaItem.FLAG_PLAYABLE)));
    }

    @Override
    public void onPuckCallChanged() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackState.ACTION_PLAY_FROM_SEARCH | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_STOP | PlaybackState.ACTION_PLAY_PAUSE;
        int state;
        switch (call.state()) {
            case CALLING: state = PlaybackState.STATE_CONNECTING; break;
            case ACTIVE: state = PlaybackState.STATE_PLAYING; break;
            default: state = call.isError() ? PlaybackState.STATE_ERROR : PlaybackState.STATE_STOPPED;
        }
        PlaybackState.Builder playback = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f);
        if (call.isError()) playback.setErrorMessage(call.status());
        session.setPlaybackState(playback.build());

        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, CALL_ID)
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Puck")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, call.status())
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, icon)
                .build());
    }

    private void startCall() {
        if (call.state() != PuckCall.State.IDLE) return;
        if (!PuckCall.hasMicPermission(this)) {
            call.fail("Open Puck on your phone once to allow the microphone.");
        } else if (!PuckCallService.startCall(this)) {
            call.fail("Android blocked the call from starting. Open Puck on your phone once, then try again.");
        }
    }

    private Bitmap renderIcon() {
        Drawable d = getDrawable(R.mipmap.ic_launcher);
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 256, 256);
        d.draw(new Canvas(b));
        return b;
    }
}
