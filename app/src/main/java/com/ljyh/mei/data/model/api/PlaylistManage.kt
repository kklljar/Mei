package com.ljyh.mei.data.model.api

import com.google.gson.annotations.SerializedName


/**
 * 创建歌单请求参数
 */
data class CreatePlaylist(
    val name: String,
    val privacy: String = "0", // 0 普通歌单, 10 隐私歌单
    val type: String = "NORMAL" // 默认 NORMAL, VIDEO 视频歌单, SHARED 共享歌单
)

/**
 * 收藏/取消收藏歌单请求参数
 */
data class SubscribePlaylist(
    val id: String,
    val checkToken: String? = null
)

/**
 * 删除歌单请求参数
 */
data class DeletePlaylist(
    val ids: String // 歌单ID，格式: "[12345]"
)

/**
 * 收藏/取消收藏歌单响应结果
 */
data class SubscribePlaylistResult(
    val code: Int,
    val message: String
)


class CreatePlaylistResult(
    @SerializedName("code")
    val code: Int,
    @SerializedName("playlist")
    val playlist: Any? = null,
    @SerializedName("id")
    val id: Long = 0
)
