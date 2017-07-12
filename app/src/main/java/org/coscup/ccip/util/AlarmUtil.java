package org.coscup.ccip.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.google.gson.internal.bind.util.ISO8601Utils;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import org.coscup.ccip.activity.SubmissionDetailActivity;
import org.coscup.ccip.model.Submission;

public class AlarmUtil {

    public static void setSubmissionAlarm(Context context, Submission submission) {
        try {
            Date date = ISO8601Utils.parse(submission.getStart(), new ParsePosition(0));
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);

            Intent intent = new Intent(context, SubmissionAlarmReceiver.class);
            intent.putExtra(SubmissionDetailActivity.INTENT_EXTRA_PROGRAM, JsonUtil.toJson(submission));

            PendingIntent pendingIntent = PendingIntent
                    .getBroadcast(context, submission.hashCode(), intent, 0);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static void cancelSubmissionAlarm(Context context, Submission submission) {
        Intent intent = new Intent(context, SubmissionDetailActivity.class);
        intent.putExtra(SubmissionDetailActivity.INTENT_EXTRA_PROGRAM, JsonUtil.toJson(submission));

        PendingIntent pendingIntent = PendingIntent
                .getBroadcast(context, submission.hashCode(), intent, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }
}

