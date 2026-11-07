package com.pulse.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

public class SwipeUpPlayerBar extends LinearLayout {

    public interface OnSwipeUpListener {
        void onSwipeUp();
    }

    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean swipingUp;
    private OnSwipeUpListener listener;

    public SwipeUpPlayerBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnSwipeUpListener(OnSwipeUpListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (swipingUp) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                swipingUp = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (-dy > touchSlop && -dy > Math.abs(dx)) {
                    swipingUp = true;
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (swipingUp) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                swipingUp = false;
                if (listener != null) listener.onSwipeUp();
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swipingUp = false;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
