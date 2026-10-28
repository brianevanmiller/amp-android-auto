package com.brianevanmiller.puck;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** The car screen: one big Call / Hang up button. Templates keep it usable while driving. */
public class PuckScreen extends Screen implements PuckCall.Listener {
    PuckScreen(@NonNull CarContext carContext) {
        super(carContext);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onCreate(@NonNull LifecycleOwner owner) {
                PuckCall.get(getCarContext()).addListener(PuckScreen.this);
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                PuckCall.get(getCarContext()).removeListener(PuckScreen.this);
            }
        });
    }

    @Override
    public void onPuckCallChanged() {
        invalidate();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        PuckCall call = PuckCall.get(getCarContext());
        boolean onCall = call.state() != PuckCall.State.IDLE;

        Action.Builder button = new Action.Builder()
                .setTitle(onCall ? "Hang up" : "Call Puck")
                .setIcon(icon(onCall ? R.drawable.ic_call_end : R.drawable.ic_call))
                .setBackgroundColor(onCall ? CarColor.RED : CarColor.GREEN)
                .setOnClickListener(onCall ? call::hangUp : this::startCall);
        if (getCarContext().getCarAppApiLevel() >= 4) button.setFlags(Action.FLAG_PRIMARY);

        return new MessageTemplate.Builder(call.status())
                .setTitle("Puck")
                .setHeaderAction(Action.APP_ICON)
                .addAction(button.build())
                .build();
    }

    private void startCall() {
        PuckCall call = PuckCall.get(getCarContext());
        if (!PuckCall.hasMicPermission(getCarContext())) {
            call.fail("Open Puck on your phone once to allow the microphone.");
        } else if (!PuckCallService.startCall(getCarContext())) {
            call.fail("Android blocked the call from starting. Open Puck on your phone once, then try again.");
        }
    }

    private CarIcon icon(int res) {
        return new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), res)).build();
    }
}
