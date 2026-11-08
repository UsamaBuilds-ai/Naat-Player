package com.pulse.player;

import android.appwidget.AppWidgetProvider;

/** 4x1 mini Naat Player widget: artwork, title, prev / play / next. */
public class Widget4x1Provider extends AppWidgetProvider {

    @Override
    public void onUpdate(android.content.Context context, android.appwidget.AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        WidgetUpdater.update(context, appWidgetManager, appWidgetIds, 4);
    }

    @Override
    public void onEnabled(android.content.Context context) {
        super.onEnabled(context);
    }
}
