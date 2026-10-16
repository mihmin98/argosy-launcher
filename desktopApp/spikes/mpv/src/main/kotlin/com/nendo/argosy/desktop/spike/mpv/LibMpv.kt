package com.nendo.argosy.desktop.spike.mpv

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

@Suppress("FunctionName")
interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_command_async(ctx: Pointer, replyUserdata: Long, args: Array<String?>): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_error_string(error: Int): String
    fun mpv_event_name(event: Int): String
    fun mpv_terminate_destroy(ctx: Pointer)

    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: UpdateCallback?, callbackCtx: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)

    fun interface UpdateCallback : Callback {
        fun invoke(callbackCtx: Pointer?)
    }

    companion object {
        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_LOG_MESSAGE = 2
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_VIDEO_RECONFIG = 17

        const val END_FILE_REASON_ERROR = 4

        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20

        const val RENDER_UPDATE_FRAME = 1L

        val INSTANCE: LibMpv by lazy {
            CLib.INSTANCE.setlocale(if (Platform.isMac()) CLib.LC_NUMERIC_DARWIN else CLib.LC_NUMERIC_GLIBC, "C")
            Native.load("mpv", LibMpv::class.java)
        }
    }
}

@Suppress("FunctionName")
interface CLib : Library {
    fun setlocale(category: Int, locale: String): String?

    companion object {
        const val LC_NUMERIC_GLIBC = 1
        const val LC_NUMERIC_DARWIN = 4

        val INSTANCE: CLib by lazy { Native.load(Platform.C_LIBRARY_NAME, CLib::class.java) }
    }
}

/**
 * A NULL-terminated `mpv_render_param[]`, laid out as `{ int type; void *data; }` per entry.
 */
fun renderParams(vararg entries: Pair<Int, Pointer?>): Memory {
    val entrySize = 2L * Native.POINTER_SIZE
    val memory = Memory(entrySize * (entries.size + 1))
    memory.clear()
    entries.forEachIndexed { index, (type, data) ->
        memory.setInt(entrySize * index, type)
        memory.setPointer(entrySize * index + Native.POINTER_SIZE, data)
    }
    return memory
}

fun cString(value: String): Memory {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val memory = Memory(bytes.size + 1L)
    memory.write(0, bytes, 0, bytes.size)
    memory.setByte(bytes.size.toLong(), 0)
    return memory
}

fun LibMpv.checked(call: String, result: Int) {
    if (result < 0) error("$call failed: ${mpv_error_string(result)} ($result)")
}

fun LibMpv.propertyString(ctx: Pointer, name: String): String? {
    val value = mpv_get_property_string(ctx, name) ?: return null
    return try {
        value.getString(0, "UTF-8")
    } finally {
        mpv_free(value)
    }
}
