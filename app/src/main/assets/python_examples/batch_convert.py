"""Batch-convert all audio/video files in inbox to a target extension.

Usage from FFmpeg GUI: provide args, e.g.  mp4
The script uses the app's FFmpeg bridge and writes into outputs/python/.
"""
import os
import shlex

import ffmpeg_gui

ROOT = ffmpeg_gui.workspace_root()

MEDIA_EXTS = {".mp4", ".mkv", ".mov", ".avi", ".webm", ".m4v", ".ts", ".3gp",
              ".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg", ".opus"}
AUDIO_OUTPUTS = {"mp3", "m4a", "wav", "flac", "opus", "aac"}


def main(argv):
    target = (argv[0] if argv else "mp4").lstrip(".").lower()
    entries = [e for e in ffmpeg_gui.workspace_entries()
               if not e["is_dir"] and os.path.splitext(e["path"])[1].lower() in MEDIA_EXTS]
    if not entries:
        print("No source media found.")
        return "Nothing to do"

    converted = 0
    out_dir = os.path.join(ROOT, "outputs", "python")
    os.makedirs(out_dir, exist_ok=True)
    for entry in entries:
        source = os.path.join(ROOT, entry["path"])
        base = os.path.splitext(os.path.basename(source))[0]
        output = os.path.join(out_dir, f"{base}.{target}")
        if target in AUDIO_OUTPUTS:
            args = f'-y -i "{source}" -vn -c:a aac -b:a 192k "{output}"'
            if target == "mp3":
                args = f'-y -i "{source}" -vn -c:a libmp3lame -b:a 320k "{output}"'
            elif target == "wav":
                args = f'-y -i "{source}" -vn -c:a pcm_s16le "{output}"'
            elif target == "flac":
                args = f'-y -i "{source}" -vn -c:a flac "{output}"'
        else:
            args = f'-y -i "{source}" -c:v libx264 -preset medium -crf 23 -c:a aac -b:a 192k "{output}"'
        print(f"Converting {source} -> {output}")
        ffmpeg_gui.run_ffmpeg(args)
        converted += 1

    ffmpeg_gui.refresh_library()
    print(f"Converted {converted} file(s) to {target}.")
    return f"Converted {converted} file(s)"


if __name__ == "__main__":
    main([])
