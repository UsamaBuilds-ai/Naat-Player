package com.pulse.player;

import android.appwidget.AppWidgetProvider;

/** 4x2 Naat Player widget: bigger artwork, progress and prev / play / next. */
public class Widget4x2Provider extends AppWidgetProvider {

    @Override
    public void onUpdate(android.content.Context context, android.appwidget.AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        WidgetUpdater.update(context, appWidgetManager, appWidgetIds, 2);
    }
}
