"""Builds the SwagKeys emoji asset from Unicode emoji-test.txt.

Output format (UTF-8): first line is a "//" comment; '@<group-id> <tab-icon>' starts a group; every other line is a base emoji
followed by its skin-tone variants, separated by single spaces.
"""
import sys
from collections import OrderedDict

GROUPS = OrderedDict([
    ("Smileys & Emotion", ("smileys", "😀")),
    ("People & Body", ("people", "👋")),
    ("Animals & Nature", ("nature", "🐶")),
    ("Food & Drink", ("food", "🍔")),
    ("Travel & Places", ("travel", "🚗")),
    ("Activities", ("activities", "⚽")),
    ("Objects", ("objects", "💡")),
    ("Symbols", ("symbols", "❤️")),
    ("Flags", ("flags", "🏁")),
])
TONES = set(range(0x1F3FB, 0x1F400))

TONE_NAMES = ["medium-light skin tone", "medium-dark skin tone", "light skin tone",
              "medium skin tone", "dark skin tone"]


def base_names(name):
    """Candidate base names for a toned emoji: tone descriptors removed, then the part before ':'."""
    stripped = name
    for t in TONE_NAMES:
        stripped = stripped.replace(", " + t, "").replace(t + ", ", "").replace(t, "")
    stripped = stripped.strip().rstrip(":").strip()
    return [stripped, name.split(":", 1)[0].strip()]


src, out = sys.argv[1], sys.argv[2]
group = None
order = OrderedDict((g, []) for g in GROUPS)   # group -> list of base emoji
variants = {}                                   # base -> list of toned variants
base_by_name = {}
toned = []                                      # (seq, name)
version = None
for line in open(src, encoding="utf-8"):
    if line.startswith("# Version:"):
        version = line.split(":", 1)[1].strip()
    if line.startswith("# group:"):
        group = line.split(":", 1)[1].strip()
        continue
    if not line.strip() or line.startswith("#"):
        continue
    data, _, comment = line.partition("#")
    cps, status = [x.strip() for x in data.split(";")]
    if status != "fully-qualified" or group not in GROUPS:
        continue
    seq = "".join(chr(int(c, 16)) for c in cps.split())
    # Comment: "<emoji> E<version> <name>"
    name = comment.strip().split(" ", 2)[2]
    if any(ord(c) in TONES for c in seq):
        toned.append((seq, name))
    else:
        order[group].append(seq)
        base_by_name[name] = seq
        variants[seq] = []

orphans = []
for seq, name in toned:
    base = next((base_by_name[n] for n in base_names(name) if n in base_by_name), None)
    if base is None:
        orphans.append(f"{seq} ({name})")
        continue
    variants[base].append(seq)

if orphans:
    sys.exit("orphan toned emoji: " + "; ".join(orphans))

n_base = sum(len(v) for v in order.values())
n_var = sum(len(v) for v in variants.values())
with open(out, "w", encoding="utf-8") as f:
    f.write(f"// Generated from Unicode emoji-test.txt, Emoji {version}: {n_base} emoji, {n_var} skin-tone variants.\n")
    for g, items in order.items():
        gid, icon = GROUPS[g]
        f.write(f"@{gid} {icon}\n")
        for e in items:
            f.write(" ".join([e] + variants[e]) + "\n")
print(f"Emoji {version}: base={n_base} variants={n_var} total={n_base + n_var}")
for g, items in order.items():
    print(f"  {GROUPS[g][0]:<11} {len(items):>5} base, {sum(len(variants[e]) for e in items):>5} variants")
print("max variants per base:", max(len(v) for v in variants.values()))
