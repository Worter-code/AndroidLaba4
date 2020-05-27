package com.example.laba4;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Widget extends AppWidgetProvider {

    public static final String ACTION_SCHEDULED_ALARM = "com.example.laba4.SCHEDULED_ALARM";
    private static final String ACTION_SCHEDULED_UPDATE = "com.example.laba4.SCHEDULED_UPDATE";
    private static final String TAG = "Widget.java";
    private static final int DAY_OF_MONTH = (24 * 60 * 60 * 1000);
    private static boolean restartFlag = false;

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, Widget.class);
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null) {
            switch (intent.getAction()) {
                case Intent.ACTION_BOOT_COMPLETED: // We need to update, as all alarms are cancelled after phone reboot
                    restartFlag = true;
                    // fall through
                case Intent.ACTION_TIMEZONE_CHANGED:  // Self Explanatory
                case Intent.ACTION_TIME_CHANGED:

                case ACTION_SCHEDULED_UPDATE:
                    AppWidgetManager manager = AppWidgetManager.getInstance(context);
                    int[] ids = manager.getAppWidgetIds(getComponentName(context));
                    Log.i(TAG, "Size:" + ids.length);
                    onUpdate(context, manager, ids);
                    break;

                case ACTION_SCHEDULED_ALARM:
                    AppWidgetManager manager1 = AppWidgetManager.getInstance(context);
                    int id = (manager1.getAppWidgetIds(getComponentName(context)))[0];
                    WidgetConfig.setNotifShown(context, id, true);

                    showNotification(context);

                    break;
            }
        }

        super.onReceive(context, intent);


    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetConfig.deleteDatePref(context, appWidgetId);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Intent intent = new Intent(context, WidgetConfig.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        views.setOnClickPendingIntent(R.id.contentFrame,pendingIntent);

        //Update counter
        //Schedule notification if zero days left
        if (!WidgetConfig.isDone(context,appWidgetId)) {
            // Get event date from shared preferences and convert it from string to long
            Calendar calendar = Calendar.getInstance();
            String widgetDate = WidgetConfig.loadDatePref(context, appWidgetId);

            long timeInMilliseconds = 0l;
            if (!widgetDate.equals("")) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                try {
                    Date mDate = sdf.parse(widgetDate);
                    timeInMilliseconds = mDate.getTime();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            // Calculate the difference in days between now and event date
            double diffInDays = (double) (timeInMilliseconds - calendar.getTimeInMillis()) / DAY_OF_MONTH;

            int daysLeftCeil = (int) Math.ceil(diffInDays);
            views.setTextViewText(R.id.counterTv, String.valueOf(Math.max(0, daysLeftCeil)));


            Log.i(TAG, "Days diff for W:(" + appWidgetId + "): " + String.format(Locale.getDefault(), "%.2f", diffInDays));
            if (daysLeftCeil == 0) {
                // If event date reached, set reminder for same day at 9:00 AM
                scheduleAlarm(context, appWidgetId);
                WidgetConfig.setDone(context, appWidgetId, true);

            } else if (diffInDays > 0) {
                // Adjust counter text font size according to value
                if (diffInDays < 100) {
                    views.setTextViewTextSize(R.id.counterTv, TypedValue.COMPLEX_UNIT_SP, 95);
                    views.setViewPadding(R.id.counterTv, 0, 0, 0, 0);

                } else {
                    int bottomMargin = (int) context.getResources().getDimension(R.dimen.counter_text_view_bottom_margin);
                    views.setTextViewTextSize(R.id.counterTv, TypedValue.COMPLEX_UNIT_SP, 65);
                    views.setViewPadding(R.id.counterTv, 0, 0, 0, bottomMargin);
                }

                // Set next update exactly at midnight, if event date not yet reached
                scheduleNextUpdate(context, appWidgetId);
            }

        } else {
            // We should still update done events, but we don't need to schedule updates for them
            views.setTextViewText(R.id.counterTv, String.valueOf(0));

            // Reschedule alarm if phone was restarted, and alarm wasn't shown yet
            // (since alarms get cleared on phone restart)
            boolean alarmShown = WidgetConfig.wasNotifShown(context, appWidgetId);
            if (restartFlag && !alarmShown) {
                scheduleAlarm(context, appWidgetId);
                restartFlag = false;
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void scheduleNextUpdate(Context context, int appWidgetId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, Widget.class).setAction(ACTION_SCHEDULED_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        long midnightTime = getTimeTillHour(0) + DAY_OF_MONTH; // 0 == HOUR_MIDNIGHT
        Log.i(TAG, "Update for W:(" + appWidgetId + ") at, in ms : " + midnightTime);

        // Remove any previous pending intent.
        alarmManager.cancel(pendingIntent);

        // Schedule to update when convenient for the system, will not wakeup device
        alarmManager.set(AlarmManager.RTC, midnightTime, pendingIntent);
    }

    private static void scheduleAlarm(Context context, int appWidgetId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, Widget.class).setAction(ACTION_SCHEDULED_ALARM);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        // Remove any previous pending intent.
        alarmManager.cancel(pendingIntent);

        long alarmTime = getTimeTillHour(9); // till 9:00 AM
        Log.i(TAG, "Alarm for W:(" + appWidgetId + ") at, in ms : " + alarmTime);

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
    }

    private static long getTimeTillHour(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);

        // One second later to be sure we are within the breakpoint
        calendar.set(Calendar.SECOND, 1);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }

    public static String getDate(long milliSeconds, String dateFormat) {
        // Create a DateFormatter object for displaying date in specified format.
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.getDefault());

        // Create a calendar object that will convert the date and time value in milliseconds to date.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliSeconds);
        return formatter.format(calendar.getTime());
    }

    private static void showNotification(Context context) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create a notification channel for the application (requirement of new API:26)
        String CHANNEL_ID = "alarm_ch_1";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mNotificationManager.createNotificationChannel( createNotificationChannel(CHANNEL_ID) );
        }

        NotificationCompat.Builder mBuilder =   new NotificationCompat.Builder(context,CHANNEL_ID)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_event)
                .setContentTitle("Scheduled event is today!")
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0)) // clear notification after click
                .setAutoCancel(true);

        mNotificationManager.notify(1, mBuilder.build());
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationChannel createNotificationChannel(String CHANNEL_ID) {
        NotificationChannel mChannel = new NotificationChannel(
                CHANNEL_ID,
                "Widget Alarms",
                NotificationManager.IMPORTANCE_DEFAULT);
        mChannel.setDescription("Shows alarms from countdown widgets");
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.YELLOW);
        mChannel.enableVibration(true);
        mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
        mChannel.setShowBadge(false);

        return mChannel;
    }

}
