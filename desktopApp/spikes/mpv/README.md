# P0-10 spike: libmpv in Compose

Throwaway. Not part of the shipped app: it is the `mpvSpike` source set of
`:desktopApp`, outside `main`, and nothing in `main` depends on it. Phase 5 builds the
real player (`MpvPlayerEngine`, P5-02) from what this proves.

Render path: strategy 1 of the plan, `MPV_RENDER_API_TYPE_SW` into a pool of three
64-byte-aligned buffers; each buffer backs a Skia `Image` through
`Image.makeFromPixmap`, which shares the pixels rather than copying them, and a
Compose `Canvas` draws the newest one. Format `bgr0` = Skia `BGRA_8888` opaque.

## Run

Needs libmpv on the library path (`libmpv.so` on Linux) and, for URLs, yt-dlp.

```
./gradlew :desktopApp:runMpvSpike --args='<file-or-url> [flags]'
```

Relative paths resolve against `desktopApp/build/mpv-spike/`.

| Flag | Default | Meaning |
|---|---|---|
| `--seconds=N` | 30 | run length, then exit |
| `--warmup=N` | 5 | seconds excluded from the `SUMMARY` line |
| `--hwdec=no\|auto-copy` | `no` | mpv `hwdec` |
| `--render-size=WxH` | canvas size | mpv render target size |
| `--window=WxH` | 1920x1080 | window size in dp |
| `--dump=file.png` | none | writes the frame on screen at the end of warmup |
| `--mute=no` | `yes` | audio on |
| `--ytdl-path=PATH` | `/usr/bin/yt-dlp` | `ytdl_hook-ytdl_path` |
| `--ytdl-format=F` | `bestvideo[height<=?1080]+bestaudio/best` | mpv `ytdl-format` |
| `--mpv-<option>=<value>` | none | any other mpv option |

Each second prints rendered and drawn frame rates, process CPU (100% = one core),
render-thread CPU, and mpv's drop counters; `SUMMARY` averages from the end of warmup.

Test clips (H.264, testsrc2 plus temporal noise):

```
ffmpeg -f lavfi -i "testsrc2=size=1920x1080:rate=60,noise=alls=12:allf=t" -t 20 \
  -c:v libx264 -preset veryfast -b:v 12M -maxrate 16M -bufsize 24M -pix_fmt yuv420p h264-1080p60.mp4
ffmpeg -f lavfi -i "testsrc2=size=3840x2160:rate=30,noise=alls=12:allf=t" -t 20 \
  -c:v libx264 -preset veryfast -b:v 35M -maxrate 45M -bufsize 70M -pix_fmt yuv420p h264-2160p30.mp4
```

## Host requirements found

- libmpv refuses to start (`mpv_create` returns NULL) unless `LC_NUMERIC` is `C`, and
  the JVM inherits the user's locale: `setlocale(LC_NUMERIC, "C")` runs before
  `mpv_create`.
- Skia's native library must be loaded (`org.jetbrains.skiko.Library.load()`) before
  the render thread builds a `Pixmap`; Compose loads it only once its window starts.
