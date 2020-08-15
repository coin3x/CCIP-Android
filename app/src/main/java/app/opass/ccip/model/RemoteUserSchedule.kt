package app.opass.ccip.model

import com.google.gson.annotations.SerializedName

data class RemoteUserSchedule(
    @SerializedName("registered")
    val registeredSessionIds: Set<String>
)
