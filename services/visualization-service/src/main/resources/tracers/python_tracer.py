#!/usr/bin/env python3
"""
AlgoVerse Python execution tracer.
Reads code from stdin (format: code\n---INPUT---\ninput),
executes with sys.settrace, emits JSON steps to stdout.
"""
import sys, json, io, traceback, builtins, copy

MAX_STEPS = int(sys.argv[1]) if len(sys.argv) > 1 else 500
TIMEOUT_S = int(sys.argv[2]) if len(sys.argv) > 2 else 10

# ── Read payload ──────────────────────────────────────────────────────────────
raw = sys.stdin.read()
if "---INPUT---" in raw:
    code_part, input_part = raw.split("---INPUT---", 1)
else:
    code_part, input_part = raw, ""
code_part = code_part.strip()

# ── Capture setup ─────────────────────────────────────────────────────────────
steps = []
stdout_capture = io.StringIO()
step_count = [0]

PRIMITIVES = (int, float, str, bool, type(None))

def safe_repr(val, depth=0):
    if depth > 3:
        return "..."
    if isinstance(val, PRIMITIVES):
        return repr(val)
    if isinstance(val, (list, tuple)):
        inner = [safe_repr(v, depth+1) for v in val[:20]]
        if len(val) > 20:
            inner.append(f"... ({len(val)} total)")
        bracket = ("[", "]") if isinstance(val, list) else ("(", ")")
        return bracket[0] + ", ".join(inner) + bracket[1]
    if isinstance(val, dict):
        items = [f"{safe_repr(k,depth+1)}: {safe_repr(v,depth+1)}"
                 for k, v in list(val.items())[:10]]
        if len(val) > 10:
            items.append(f"... ({len(val)} keys)")
        return "{" + ", ".join(items) + "}"
    if isinstance(val, set):
        return "{" + ", ".join(safe_repr(v, depth+1) for v in list(val)[:10]) + "}"
    return type(val).__name__

def snapshot_locals(frame_locals):
    result = {}
    for k, v in frame_locals.items():
        if k.startswith("_"):
            continue
        try:
            result[k] = safe_repr(v)
        except Exception:
            result[k] = "<error>"
    return result

def tracer(frame, event, arg):
    if step_count[0] >= MAX_STEPS:
        return None

    # Skip tracer internals
    if frame.f_code.co_filename != "<string>":
        return tracer

    if event in ("call", "line", "return", "exception"):
        step_count[0] += 1
        step = {
            "step": step_count[0],
            "event": event,
            "line": frame.f_lineno,
            "function": frame.f_code.co_name,
            "locals": snapshot_locals(dict(frame.f_locals)),
            "stdout": stdout_capture.getvalue(),
        }
        if event == "return":
            step["returnValue"] = safe_repr(arg)
        if event == "exception":
            step["exception"] = str(arg[1]) if arg else ""
        steps.append(step)

    return tracer

# ── Execute ───────────────────────────────────────────────────────────────────
sys_stdin_orig = sys.stdin
sys_stdout_orig = sys.stdout
sys.stdin  = io.StringIO(input_part)
sys.stdout = stdout_capture

error_msg = None
try:
    compiled = compile(code_part, "<string>", "exec")
    sys.settrace(tracer)
    exec(compiled, {"__name__": "__main__", "__builtins__": builtins})
except Exception as e:
    error_msg = traceback.format_exc()
finally:
    sys.settrace(None)
    sys.stdin  = sys_stdin_orig
    sys.stdout = sys_stdout_orig

# ── Emit ──────────────────────────────────────────────────────────────────────
output = {
    "language": "python",
    "steps": steps,
    "finalOutput": stdout_capture.getvalue(),
    "error": error_msg,
    "truncated": step_count[0] >= MAX_STEPS,
}
print(json.dumps(output))
