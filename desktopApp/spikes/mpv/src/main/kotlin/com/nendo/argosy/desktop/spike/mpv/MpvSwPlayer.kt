package com.nendo.argosy.desktop.spike.mpv

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skiko.Library
import java.lang.management.ManagementFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class PixelSize(val width: Int, val height: Int)

/**
 * A pixel buffer mpv renders into and Skia draws from without copying.
 * [image] shares [pixels]; the buffer must not be rendered into while its image is on screen.
 */
class SwFrame internal constructor(val size: PixelSize) {
    private val stride = (size.width * 4 + 63) / 64 * 64
    private val backing = Memory(stride.toLong() * size.height + 64)
    private val pixels: Pointer = backing.align(64)
    private val sizeParam = Memory(8).apply {
        setInt(0, size.width)
        setInt(4, size.height)
    }
    private val formatParam = cString("bgr0")
    private val strideParam = Memory(Native.SIZE_T_SIZE.toLong()).apply {
        if (Native.SIZE_T_SIZE == 8) setLong(0, stride.toLong()) else setInt(0, stride)
    }
    internal val params = renderParams(
        LibMpv.RENDER_PARAM_SW_SIZE to sizeParam,
        LibMpv.RENDER_PARAM_SW_FORMAT to formatParam,
        LibMpv.RENDER_PARAM_SW_STRIDE to strideParam,
        LibMpv.RENDER_PARAM_SW_POINTER to pixels,
    )
    private val pixmap = Pixmap.make(
        ImageInfo(size.width, size.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
        Pointer.nativeValue(pixels),
        stride,
    )
    val image: Image = Image.makeFromPixmap(pixmap)
}

/**
 * libmpv playing through `MPV_RENDER_API_TYPE_SW` into a pool of [SwFrame]s.
 * [onFrame] receives each rendered frame on the render thread; the receiver hands the
 * frame it replaces back through [release].
 */
class MpvSwPlayer(
    options: Map<String, String>,
    private val forcedSize: PixelSize?,
    private val onFrame: (SwFrame) -> Unit,
    private val onEnd: (String) -> Unit,
) : AutoCloseable {
    private val mpv = LibMpv.INSTANCE
    val handle: Pointer = mpv.mpv_create() ?: error("mpv_create returned NULL")
    private val renderContext: Pointer
    private val wakeLock = Object()
    private var framePending = false
    @Volatile private var running = true
    private val targetSize = AtomicReference(PixelSize(0, 0))
    private val free = ArrayBlockingQueue<SwFrame>(POOL_SIZE)
    private val retired = ConcurrentLinkedQueue<SwFrame>()
    @Volatile private var poolSize: PixelSize? = null
    private val updateCallback = LibMpv.UpdateCallback {
        synchronized(wakeLock) {
            framePending = true
            wakeLock.notifyAll()
        }
    }

    val renderedFrames = AtomicLong()
    val starvedFrames = AtomicLong()

    private val renderThread = Thread(::renderLoop, "mpv-sw-render")
    private val eventThread = Thread(::eventLoop, "mpv-events")

    init {
        Library.load()
        options.forEach { (name, value) ->
            mpv.checked("set $name=$value", mpv.mpv_set_option_string(handle, name, value))
        }
        mpv.checked("mpv_initialize", mpv.mpv_initialize(handle))
        val result = PointerByReference()
        val apiType = cString("sw")
        mpv.checked(
            "mpv_render_context_create",
            mpv.mpv_render_context_create(result, handle, renderParams(LibMpv.RENDER_PARAM_API_TYPE to apiType)),
        )
        renderContext = result.value
        mpv.mpv_render_context_set_update_callback(renderContext, updateCallback, null)
        renderThread.start()
        eventThread.start()
    }

    fun load(uri: String) {
        mpv.checked("loadfile", mpv.mpv_command_async(handle, 0, arrayOf("loadfile", uri, null)))
    }

    fun resize(size: PixelSize) {
        targetSize.set(size)
    }

    fun release(frame: SwFrame) {
        if (frame.size == poolSize) free.offer(frame) else retired.add(frame)
    }

    fun renderThreadCpuNanos(): Long = ManagementFactory.getThreadMXBean().getThreadCpuTime(renderThread.threadId())

    fun property(name: String): String? = mpv.propertyString(handle, name)

    private fun renderLoop() {
        while (running) {
            synchronized(wakeLock) {
                while (!framePending && running) wakeLock.wait()
                framePending = false
            }
            if (!running) break
            if (mpv.mpv_render_context_update(renderContext) and LibMpv.RENDER_UPDATE_FRAME == 0L) continue
            val frame = acquire(forcedSize ?: targetSize.get().takeIf { it.width > 0 && it.height > 0 } ?: FALLBACK)
            if (frame == null) {
                starvedFrames.incrementAndGet()
                continue
            }
            mpv.checked("mpv_render_context_render", mpv.mpv_render_context_render(renderContext, frame.params))
            renderedFrames.incrementAndGet()
            onFrame(frame)
        }
    }

    private fun acquire(size: PixelSize): SwFrame? {
        if (size != poolSize) {
            free.drainTo(retired)
            poolSize = size
            repeat(POOL_SIZE) { free.offer(SwFrame(size)) }
        }
        return free.poll(ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun eventLoop() {
        while (running) {
            val event = mpv.mpv_wait_event(handle, 0.5)
            when (event.getInt(0)) {
                LibMpv.EVENT_NONE -> Unit
                LibMpv.EVENT_SHUTDOWN -> return
                LibMpv.EVENT_END_FILE -> {
                    val data = event.getPointer(16)
                    val reason = data.getInt(0)
                    val error = data.getInt(4)
                    onEnd(if (reason == LibMpv.END_FILE_REASON_ERROR) "error: ${mpv.mpv_error_string(error)}" else "reason $reason")
                }
                else -> println("mpv event: ${mpv.mpv_event_name(event.getInt(0))}")
            }
        }
    }

    override fun close() {
        running = false
        synchronized(wakeLock) { wakeLock.notifyAll() }
        renderThread.join()
        mpv.mpv_render_context_set_update_callback(renderContext, null, null)
        mpv.mpv_render_context_free(renderContext)
        mpv.mpv_wakeup(handle)
        eventThread.join()
        mpv.mpv_terminate_destroy(handle)
    }

    private companion object {
        const val POOL_SIZE = 3
        const val ACQUIRE_TIMEOUT_MS = 100L
        val FALLBACK = PixelSize(16, 16)
    }
}
