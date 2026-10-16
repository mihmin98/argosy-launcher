package com.nendo.argosy.desktop.spike.mpv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.delay
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Rect
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

data class SpikeConfig(
    val uri: String,
    val seconds: Int,
    val warmupSeconds: Int,
    val renderSize: PixelSize?,
    val window: PixelSize,
    val mpvOptions: Map<String, String>,
    val dumpPath: String?,
) {
    companion object {
        fun parse(args: Array<String>): SpikeConfig {
            val flags = args.filter { it.startsWith("--") }.associate {
                val body = it.removePrefix("--")
                body.substringBefore('=') to body.substringAfter('=', "yes")
            }
            val uri = args.singleOrNull { !it.startsWith("--") }
                ?: error("usage: runMpvSpike --args='<file-or-url> [--seconds=N] [--hwdec=no|auto-copy] [--render-size=WxH] [--window=WxH] [--mpv-<option>=<value>]'")
            val isUrl = uri.contains("://")
            val options = linkedMapOf(
                "vo" to "libmpv",
                "terminal" to "yes",
                "msg-level" to "all=warn,ytdl_hook=info",
                "hwdec" to (flags["hwdec"] ?: "no"),
                "mute" to (flags["mute"] ?: "yes"),
                "script-opts" to "ytdl_hook-ytdl_path=${flags["ytdl-path"] ?: "/usr/bin/yt-dlp"}",
                "ytdl-format" to (flags["ytdl-format"] ?: "bestvideo[height<=?1080]+bestaudio/best"),
            )
            if (!isUrl) options["loop-file"] = "inf"
            flags.filterKeys { it.startsWith("mpv-") }.forEach { (key, value) -> options[key.removePrefix("mpv-")] = value }
            return SpikeConfig(
                uri = uri,
                seconds = flags["seconds"]?.toInt() ?: 30,
                warmupSeconds = flags["warmup"]?.toInt() ?: 5,
                renderSize = flags["render-size"]?.let(::parseSize),
                window = flags["window"]?.let(::parseSize) ?: PixelSize(1920, 1080),
                mpvOptions = options,
                dumpPath = flags["dump"],
            )
        }

        private fun parseSize(value: String): PixelSize {
            val (width, height) = value.lowercase().split('x').map(String::toInt)
            return PixelSize(width, height)
        }
    }
}

private class Sample(val wallNanos: Long, val processCpuNanos: Long, val renderCpuNanos: Long, val rendered: Long, val drawn: Long)

fun main(args: Array<String>) {
    val config = SpikeConfig.parse(args)
    println("spike config: $config")
    val front = mutableStateOf<SwFrame?>(null)
    val drawn = AtomicLong()
    var endReason: String? = null
    lateinit var player: MpvSwPlayer
    player = MpvSwPlayer(
        options = config.mpvOptions,
        forcedSize = config.renderSize,
        onFrame = { frame ->
            SwingUtilities.invokeLater {
                val replaced = front.value
                front.value = frame
                if (replaced != null && replaced !== frame) player.release(replaced)
            }
        },
        onEnd = { reason ->
            println("end-file: $reason")
            endReason = reason
        },
    )
    player.load(config.uri)
    val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    application {
        LaunchedEffect(Unit) {
            fun sample() = Sample(System.nanoTime(), os.processCpuTime, player.renderThreadCpuNanos(), player.renderedFrames.get(), drawn.get())
            var previous = sample()
            var warm: Sample? = null
            for (second in 1..config.seconds) {
                delay(1000)
                val now = sample()
                println("t=${second}s ${report(previous, now)} | ${mpvStats(player)}")
                if (second == config.warmupSeconds) {
                    warm = now
                    config.dumpPath?.let { path -> front.value?.let { dumpFrame(it, path) } }
                }
                previous = now
                if (endReason?.startsWith("error") == true) break
            }
            val baseline = warm
            if (baseline != null && previous !== baseline) println("SUMMARY ${config.uri} ${report(baseline, previous)} | ${mpvStats(player)}")
            exitApplication()
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "argosy mpv spike: sw render",
            state = WindowState(size = DpSize(config.window.width.dp, config.window.height.dp)),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { player.resize(PixelSize(it.width, it.height)) },
            ) {
                val frame = front.value ?: return@Canvas
                drawn.incrementAndGet()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawImageRect(frame.image, Rect.makeWH(size.width, size.height))
                }
            }
        }
    }
    player.close()
}

private fun dumpFrame(frame: SwFrame, path: String) {
    val png = frame.image.encodeToData(EncodedImageFormat.PNG) ?: error("png encode failed")
    File(path).writeBytes(png.bytes)
    println("dumped ${frame.size} frame to ${File(path).absolutePath}")
}

private fun report(from: Sample, to: Sample): String {
    val wall = (to.wallNanos - from.wallNanos) / 1e9
    val cpu = 100.0 * (to.processCpuNanos - from.processCpuNanos) / (to.wallNanos - from.wallNanos)
    val renderCpu = 100.0 * (to.renderCpuNanos - from.renderCpuNanos) / (to.wallNanos - from.wallNanos)
    return "rendered=%.1ffps drawn=%.1ffps cpu=%.0f%% render-thread=%.0f%%".format(
        (to.rendered - from.rendered) / wall,
        (to.drawn - from.drawn) / wall,
        cpu,
        renderCpu,
    )
}

private fun mpvStats(player: MpvSwPlayer): String {
    val names = listOf(
        "video-params/w", "video-params/h", "container-fps", "estimated-vf-fps",
        "hwdec-current", "frame-drop-count", "decoder-frame-drop-count",
        "vo-delayed-frame-count", "mistimed-frame-count", "osd-dimensions/w", "osd-dimensions/h",
    )
    return "starved=${player.starvedFrames.get()} " + names.joinToString(" ") { "$it=${player.property(it)}" }
}
