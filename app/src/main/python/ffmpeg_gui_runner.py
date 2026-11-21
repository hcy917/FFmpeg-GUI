"""Embedded Python entry point for FFmpeg GUI.

Kotlin calls run_file(script_path, args, libs_dir, writer). The script is
executed with __name__ == "__main__" and a main(argv) function is invoked
when present. stdout/stderr are forwarded to the app's task log.
"""
import inspect
import os
import shlex
import sys
import traceback


def run_file(path, args="", libs_dir="", writer=None):
    saved_stdout, saved_stderr = sys.stdout, sys.stderr
    if writer is not None:
        sys.stdout = writer
        sys.stderr = writer
    if libs_dir:
        libs_dir = str(libs_dir)
        if libs_dir not in sys.path:
            sys.path.insert(0, libs_dir)

    argv = shlex.split(args or "")
    globals_dict = {
        "__name__": "__main__",
        "__file__": path,
        "__builtins__": __builtins__,
        "ARGS": argv,
    }
    try:
        with open(path, "r", encoding="utf-8") as handle:
            source = handle.read()
        code = compile(source, path, "exec")
        exec(code, globals_dict)
        main_fn = globals_dict.get("main")
        if callable(main_fn):
            try:
                params = inspect.signature(main_fn).parameters
                accepts_args = any(
                    p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD, p.VAR_POSITIONAL)
                    for p in params.values()
                )
            except (TypeError, ValueError):
                accepts_args = True
            result = main_fn(argv) if accepts_args else main_fn()
            if result is not None:
                print(result)
            return result
        return "Script completed"
    except SystemExit as exc:
        return "Script exited: %s" % (exc.code,)
    except Exception:
        traceback.print_exc()
        raise
    finally:
        if writer is not None:
            sys.stdout = saved_stdout
            sys.stderr = saved_stderr
