package com.pulse.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * Mini player container that opens Now Playing when swiped up.
 *
 * The bar itself is clickable, so {@link #onInterceptTouchEvent} never sees
 * ACTION_MOVE once a child or the bar itself owns the gesture. Detection
 * therefore starts on ACTION_DOWN in both dispatch paths.
 */
public class SwipeUpPlayerBar extends LinearLayout {

    public interface OnSwipeUpListener {
        void onSwipeUp();
    }

    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean swipingUp;
    private boolean tracking;
    private OnSwipeUpListener listener;

    public SwipeUpPlayerBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnSwipeUpListener(OnSwipeUpListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                swipingUp = false;
                tracking = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracking && !swipingUp) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (-dy > touchSlop && -dy > Math.abs(dx) * 1.2f) {
                        swipingUp = true;
                        // Stop the children (and any scroll parent) from stealing the gesture.
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                break;
            default:
                break;
        }

        boolean handled = super.dispatchTouchEvent(event);

        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (swipingUp && listener != null) listener.onSwipeUp();
            swipingUp = false;
            tracking = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            swipingUp = false;
            tracking = false;
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && swipingUp) return true;
        return super.onInterceptTouchEvent(event);
    }
}
