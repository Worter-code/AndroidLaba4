package com.example.laba4;

import android.app.DatePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class WidgetConfig extends AppCompatActivity {
    private static final String PREFS_NAME = "com.example.laba4.Widget";
    private static final String PREF_PREFIX_KEY = "appwidget_";

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Calendar mCalendar = Calendar.getInstance();

    private EditText dateTv;
    private TextView errorTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.widget_config);
        setTitle("Configure Widget");

        // извлекаем ID конфигурируемого виджета
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        // формируем intent ответа
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        // отрицательный ответ
        setResult(RESULT_CANCELED,resultValue);

        //проверяем корректность ID
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID){
            finish();
            return;
        }


        //Setup UI
        errorTv = findViewById(R.id.errorTv);
        dateTv = findViewById(R.id.dateTv);
        dateTv.setText(loadDatePref(WidgetConfig.this, mAppWidgetId));

        final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                mCalendar.set(Calendar.YEAR, year);
                mCalendar.set(Calendar.MONTH, monthOfYear);
                mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                updateLabel();
            }
        };

        dateTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDateDialog(date);
            }
        });

        Button applyBtn = findViewById(R.id.applyBtn);
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCalValue();

                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY,0);
                today.set(Calendar.MINUTE,0);
                today.set(Calendar.SECOND,0);
                today.set(Calendar.MILLISECOND,0);
                today.add(Calendar.DAY_OF_MONTH,1);

                if (mCalendar.before(today)) {
                    errorTv.setText("Event date must be in the future!");
                    errorTv.setVisibility(View.VISIBLE);
                } else {
                    final Context context = WidgetConfig.this;

                    // When the button is clicked, store the string locally
                    saveDatePref(context, mAppWidgetId, dateTv.getText().toString(), false, false);

                    // It is the responsibility of the configuration activity to update the app widget
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    Widget.updateAppWidget(context, appWidgetManager, mAppWidgetId);

                    // Make sure we pass back the original appWidgetId
                    Intent resultValue = new Intent();
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                    setResult(RESULT_OK, resultValue);
                    finish();
                }
            }
        });
    }

    private void showDateDialog(DatePickerDialog.OnDateSetListener date) {
        new DatePickerDialog(WidgetConfig.this, date,
                mCalendar.get(Calendar.YEAR),
                mCalendar.get(Calendar.MONTH),
                mCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateLabel() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        dateTv.setText(sdf.format(mCalendar.getTime()));
        errorTv.setVisibility(View.GONE);
    }

    private void setCalValue() {
        if (!dateTv.getText().toString().equals("")) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            try {
                mCalendar.setTime(sdf.parse(dateTv.getText().toString()));
            } catch (ParseException e) {
                // Invalid date in text view
                e.printStackTrace();
            }
        }
    }

    /* Methods that work for each widget individually, responsible for:
     *   - saving/loading/deleting counter's set date from and to application preferences
     *   - keeping track if the widget's event is done or not
     * */
    static void saveDatePref(Context context, int appWidgetId, String text, boolean done, boolean shown) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putString(PREF_PREFIX_KEY + appWidgetId, text);
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + "b", done);
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + "n", shown);
        prefs.apply();
    }

    static void deleteDatePref(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + "b");
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + "n");
        prefs.apply();
    }

    static String loadDatePref(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        String titleValue = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null);
        if (titleValue != null) {
            return titleValue;
        } else {
            return "";
        }
    }

    static Boolean isDone(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getBoolean(PREF_PREFIX_KEY + appWidgetId + "b", false);
    }

    static void setDone(Context context, int appWidgetId, boolean done) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + "b", done);
        prefs.apply();
    }

    static Boolean wasNotifShown(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getBoolean(PREF_PREFIX_KEY + appWidgetId + "n", false);
    }

    static void setNotifShown(Context context, int appWidgetId, boolean shown) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + "n", shown);
        prefs.apply();
    }

}
