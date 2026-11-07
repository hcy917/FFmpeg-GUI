"""Public helper module for FFmpeg GUI Python scripts.

Example:
    import ffmpeg_gui
    ffmpeg_gui.log("hello")
    print(ffmpeg_gui.run_ffmpeg("-i input.mp4 -c copy output.mp4"))
"""
from java import jclass

_Bridge = jclass("com.ffmpeg.gui.python.PythonBridge")


def log(message):
    _Bridge.log(str(message))


def workspace_root():
    return str(_Bridge.workspaceRoot())


def list_files(path=""):
    return str(_Bridge.listFiles(str(path)))


def read_text(path):
    return str(_Bridge.readText(str(path)))


def write_text(path, content):
    return str(_Bridge.writeText(str(path), str(content)))


def run_ffmpeg(args):
    if isinstance(args, (list, tuple)):
        args = " ".join(str(a) for a in args)
    return str(_Bridge.runFfmpeg(str(args)))


def probe(path):
    return str(_Bridge.probe(str(path)))


def list_media():
    return str(_Bridge.listMedia())


def refresh_library():
    return str(_Bridge.refreshLibrary())


def workspace_entries():
    """Return a list of dicts describing every workspace entry."""
    entries = []
    for line in list_files("").splitlines():
        if line.startswith("[dir]"):
            name = line[5:].strip().split(" (")[0].rstrip("/")
            entries.append({"path": name, "is_dir": True, "size": 0})
        elif line.startswith("[file]"):
            rest = line[6:].strip()
            path, _, tail = rest.partition(" (")
            try:
                size = int(tail.replace(" bytes)", "").strip())
            except Exception:
                size = 0
            entries.append({"path": path, "is_dir": False, "size": size})
    return entries


def media_entries():
    """Return media library entries as dicts."""
    result = []
    for line in list_media().splitlines():
        parts = [part.strip() for part in line.split("|")]
        if len(parts) >= 4:
            result.append({
                "name": parts[0],
                "kind": parts[1],
                "duration_ms": parts[2].replace("ms", ""),
                "path": parts[3],
            })
    return result
