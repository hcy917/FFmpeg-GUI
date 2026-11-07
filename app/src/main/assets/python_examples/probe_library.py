"""探测工作区中所有媒体，并输出简要报告。"""
import os

import ffmpeg_gui

ROOT = ffmpeg_gui.workspace_root()


def main(argv):
    media = ffmpeg_gui.media_entries()
    if not media:
        print("媒体库还没有内容，请先导入文件。")
        return "没有可探测的媒体"

    print("工作区：", ROOT)
    print("媒体数量：", len(media))
    print("-" * 52)
    for item in media:
        print(item["name"])
        print(f"  类型={item['kind']} 时长={item['duration_ms']} 毫秒")
        info = ffmpeg_gui.probe(os.path.relpath(item["path"], ROOT))
        for line in info.splitlines():
            if line.startswith(("封装：", "流 ")) or "编码=" in line:
                print("  " + line)
    print("-" * 52)
    print("完成。报告由内置 Python 解释器生成。")
    ffmpeg_gui.refresh_library()


if __name__ == "__main__":
    main([])
