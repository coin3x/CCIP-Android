package app.opass.ccip.util

import android.content.Context
import androidx.core.content.edit
import app.opass.ccip.model.ConfSchedule
import app.opass.ccip.model.EventConfig

object PreferenceUtil {
    private const val PREF_BEACON = "beacon"
    private const val PREF_BEACON_PERMISSION_REQUESTED = "permission_requested"
    private const val PREF_BEACON_NOTIFIED = "notified"

    private const val PREF_EVENT = "event"
    private const val PREF_CURRENT_EVENT = "current_event"

    private const val PREF_AUTH = "auth"
    private const val PREF_AUTH_TOKEN = "token"
    private const val PREF_AUTH_ROLE = "role"

    private const val PREF_SCHEDULE = "schedule"
    private const val PREF_SCHEDULE_SCHEDULE = "schedule"
    private const val PREF_SCHEDULE_STARS = "stars"

    fun setBeaconPermissionRequested(context: Context) {
        context.getSharedPreferences(PREF_BEACON, Context.MODE_PRIVATE)
            .edit(true) { putBoolean(PREF_BEACON_PERMISSION_REQUESTED, true) }
    }

    fun isBeaconPermissionRequested(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREF_BEACON, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(PREF_BEACON_PERMISSION_REQUESTED, false)
    }

    fun setBeaconNotified(context: Context) {
        context.getSharedPreferences(PREF_BEACON, Context.MODE_PRIVATE)
            .edit(true) { putBoolean(getCurrentEvent(context).eventId + PREF_BEACON_NOTIFIED, true) }
    }

    fun isBeaconNotified(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREF_BEACON, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(getCurrentEvent(context).eventId + PREF_BEACON_NOTIFIED, false)
    }

    fun setCurrentEvent(context: Context, eventConfig: EventConfig) {
        context.getSharedPreferences(PREF_EVENT, Context.MODE_PRIVATE)
            .edit(true) { putString(PREF_CURRENT_EVENT, JsonUtil.toJson(eventConfig)) }
    }

    fun getCurrentEvent(context: Context): EventConfig {
        val sharedPreferences = context.getSharedPreferences(PREF_EVENT, Context.MODE_PRIVATE)
        val currentEvent = sharedPreferences.getString(PREF_CURRENT_EVENT, "{\"event_id\": \"\"}")
        return try {
            JsonUtil.fromJson(currentEvent!!, EventConfig::class.java)
        } catch (t: Throwable) {
            JsonUtil
                .fromJson("{\"event_id\": \"\"}", EventConfig::class.java)
                .also { setCurrentEvent(context, it) }
        }
    }

    fun setToken(context: Context, token: String?) {
        context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
            .edit(true) { putString(getCurrentEvent(context).eventId + PREF_AUTH_TOKEN, token) }
    }

    fun getToken(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
        return sharedPreferences.getString(getCurrentEvent(context).eventId + PREF_AUTH_TOKEN, null)
    }

    fun setRole(context: Context, role: String?) {
        context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
            .edit(true) { putString(getCurrentEvent(context).eventId + PREF_AUTH_ROLE, role)}
    }

    fun getRole(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
        return sharedPreferences.getString(getCurrentEvent(context).eventId + PREF_AUTH_ROLE, null)
    }

    fun saveSchedule(context: Context, scheduleJson: String) {
        context.getSharedPreferences(PREF_SCHEDULE, Context.MODE_PRIVATE)
            .edit(true) { putString(getCurrentEvent(context).eventId + PREF_SCHEDULE_SCHEDULE, scheduleJson) }
    }

    fun loadSchedule(context: Context): ConfSchedule? {
        val sharedPreferences = context.getSharedPreferences(PREF_SCHEDULE, Context.MODE_PRIVATE)
        val scheduleJson =
            sharedPreferences.getString(getCurrentEvent(context).eventId + PREF_SCHEDULE_SCHEDULE, "{}")!!

        return try {
            JsonUtil.fromJson(scheduleJson, ConfSchedule::class.java)
        } catch (t: Throwable) {
            saveSchedule(context, "{}")
            JsonUtil.fromJson("{}", ConfSchedule::class.java)
        }
    }

    fun saveStarredIds(context: Context, sessionIds: List<String>) {
        context.getSharedPreferences(PREF_SCHEDULE_STARS, Context.MODE_PRIVATE)
            .edit { putStringSet(getCurrentEvent(context).eventId + PREF_SCHEDULE_STARS, sessionIds.toSet()) }
    }

    fun loadStarredIds(context: Context): List<String> {
        val sharedPreferences = context.getSharedPreferences(PREF_SCHEDULE_STARS, Context.MODE_PRIVATE)
        return try {
            sharedPreferences.getStringSet(getCurrentEvent(context).eventId + PREF_SCHEDULE_STARS, emptySet())!!
                .toList()
        } catch (t: Throwable) {
            emptyList<String>().also { saveStarredIds(context, it) }
        }
    }
}
