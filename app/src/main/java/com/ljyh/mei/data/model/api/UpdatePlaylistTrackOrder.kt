package com.ljyh.mei.data.model.api

data class UpdatePlaylistTrackOrder(
    val pid: String,
    val trackIds: String,
    val op: String = "update",
) {
    constructor(pid: String, ids: List<Long>) : this(
        pid = pid,
        trackIds = ids.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]"),
    )
}
