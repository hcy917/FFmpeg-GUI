"""汇总工作区，并找出体积最大的媒体文件。"""
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
    print(f"工作区：{ffmpeg_gui.workspace_root()}")
    print(f"文件数：{len(entries)} · 总大小：{human(total)}")
    print("-" * 52)
    for entry in entries[:12]:
        print(f"{human(entry['size']):>10}  {entry['path']}")
    libraries = os.listdir(os.path.join(ffmpeg_gui.workspace_root(), "python", "libs"))
    print("-" * 52)
    print(f"Python 库：{', '.join(libraries) if libraries else '无'}")
    return "报告完成"


if __name__ == "__main__":
    main([])
