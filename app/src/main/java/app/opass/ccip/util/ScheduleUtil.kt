package app.opass.ccip.util

import android.content.Context
import app.opass.ccip.model.Session

object ScheduleUtil {
    fun getStarredSessions(context: Context): List<Session> {
        val sessions = PreferenceUtil.loadSchedule(context)?.sessions ?: return emptyList()
        val starredIds = PreferenceUtil.loadStarredIds(context)
        return sessions.filter { starredIds.contains(it.id) }
    }

    fun getRegisteredSessions(context: Context): List<Session> {
        val sessions = PreferenceUtil.loadSchedule(context)?.sessions ?: return emptyList()
        val registeredIds = PreferenceUtil.getRegisteredIds(context) ?: return emptyList()
        return sessions.filter { registeredIds.contains(it.id) }
    }
}
