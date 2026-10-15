"""Prints javac / lint errors from build/last-build.txt compactly: file:line message + source line."""
import os
import re
import sys

log = os.path.join(os.path.dirname(__file__), "..", "build", "last-build.txt")
lines = open(log, encoding="utf-8", errors="replace").read().splitlines()
seen = set()
limit = int(sys.argv[1]) if len(sys.argv) > 1 else 80
n = 0
for i, l in enumerate(lines):
    m = re.match(r"^(?:gradlew\.bat : )?(\S+\.java):(\d+): (error|warning): (.*)$", l)
    if not m or m.group(3) != "error":
        continue
    key = (m.group(1), m.group(2), m.group(4))
    if key in seen:
        continue
    seen.add(key)
    path = m.group(1).split("foxtalgia\\")[-1] if "foxtalgia\\" in m.group(1) else m.group(1)
    src = lines[i + 1].strip() if i + 1 < len(lines) else ""
    extra = ""
    for j in range(i + 2, min(i + 6, len(lines))):
        if lines[j].strip().startswith(("symbol:", "location:", "required:", "found:", "reason:")):
            extra += " | " + lines[j].strip()
    print("%s:%s %s%s\n    %s" % (path, m.group(2), m.group(4), extra, src))
    n += 1
    if n >= limit:
        break
print(n, "errors shown")
