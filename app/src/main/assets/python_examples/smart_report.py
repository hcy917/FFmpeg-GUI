"""Summarise the workspace and identify the largest media assets."""
import os

import ffmpeg_gui


def human(size):
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return f"{size:.1f} {unit}"
        size /= 1024


def main(argv):
    entries = [e for e in ffmpeg_gui.workspace_entries() if not e["is_dir"]]
    entries.sort(key=lambda e: e["size"], reverse=True)
    total = sum(e["size"] for e in entries)
    print(f"Workspace: {ffmpeg_gui.workspace_root()}")
    print(f"Files: {len(entries)} · Total: {human(total)}")
    print("-" * 52)
    for entry in entries[:12]:
        print(f"{human(entry['size']):>10}  {entry['path']}")
    libraries = os.listdir(os.path.join(ffmpeg_gui.workspace_root(), "python", "libs"))
    print("-" * 52)
    print(f"Python libraries: {', '.join(libraries) if libraries else 'none'}")
    return "Report complete"


if __name__ == "__main__":
    main([])
