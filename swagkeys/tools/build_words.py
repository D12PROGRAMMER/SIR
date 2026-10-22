"""Builds the SwagKeys autocorrect word list from AOSP LatinIME wordlists.

Usage: build_words.py TARGETS OUT [KNOWN ...]
  TARGETS  en_US_wordlist.combined(.gz): known words and correction targets
  KNOWN    more wordlists (en_GB_wordlist, en_wordlist) whose words are only known, so
           that e.g. British spellings ("colour") are never "corrected"
All from https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/
(Apache License 2.0).

Output (UTF-8, sorted by the UTF-8 bytes of the word, one entry per line):
  "word<TAB>freq"  a correction target: lowercase a-z only, not offensive, freq 1-255
  "word"           known word only: never corrected, never suggested (proper nouns, words
                   with capitals or apostrophes, offensive words, and contractions without
                   their apostrophe such as "dont", so they are left as typed)
All words are stored lowercased.
"""
import gzip
import re
import sys

known = {}   # word -> target freq (0 = known only)
targets = 0


def read(src, with_targets):
    opener = gzip.open if src.endswith(".gz") else open
    with opener(src, "rt", encoding="utf-8") as f:
        for line in f:
            if not line.startswith(" word="):
                continue
            fields = dict(kv.split("=", 1) for kv in line.strip().split(",") if "=" in kv)
            word = fields.get("word", "")
            if not word:
                continue
            freq = int(fields.get("f", "0") or 0)
            flags = fields.get("flags", "")
            offensive = "offensive" in flags or fields.get("possibly_offensive") == "true"
            nonword = "nonword" in flags or fields.get("not_a_word") == "true"
            lw = word.lower()
            target = (with_targets and word == lw and re.fullmatch(r"[a-z]+", word) is not None
                      and not offensive and not nonword and freq > 0)
            prev = known.get(lw, 0)
            known[lw] = max(prev, freq if target else 0)
            if "'" in lw or "’" in lw:
                stripped = lw.replace("'", "").replace("’", "")
                known.setdefault(stripped, 0)


out = sys.argv[2]
read(sys.argv[1], True)
for extra in sys.argv[3:]:
    read(extra, False)

lines = []
for w, fq in known.items():
    if "\n" in w or "\t" in w:
        continue
    lines.append((w.encode("utf-8"), w, fq))
lines.sort()
with open(out, "w", encoding="utf-8") as f:
    for _, w, fq in lines:
        if fq > 0:
            f.write(f"{w}\t{fq}\n")
            targets += 1
        else:
            f.write(f"{w}\n")
print(f"{len(lines)} known words, {targets} correction targets")
