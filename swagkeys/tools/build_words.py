"""Builds the SwagKeys autocorrect word list from AOSP LatinIME wordlists.

Usage: build_words.py TARGETS OUT [KNOWN ...]
  TARGETS  en_US_wordlist.combined(.gz): known words and correction targets
  KNOWN    more wordlists (en_GB_wordlist, en_wordlist) whose words are only known, so
           that e.g. British spellings ("colour") are never "corrected"
All from https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/
(Apache License 2.0).

Output (UTF-8, LF line ends, sorted by the UTF-8 bytes of the word, one entry per line, all
words lowercased, frequencies 0-255 from the source lists):
  "word<TAB>F"   a correction target: lowercase a-z only, not offensive, F > 0 (TARGETS only)
  "word<TAB>=F"  known: never corrected; competes as a candidate that is never returned
                 (proper nouns, offensive words, other lists' words, contractions without
                 their apostrophe such as "dont", and possessives such as "kates" from
                 "Kate's", so they are left as typed)
  "word<TAB>~F"  weak: another capitalized word without its apostrophe ("oday" from "O'Day");
                 when typed it competes as itself instead of blocking corrections outright
"""
import gzip
import re
import sys

TARGET, KNOWN, WEAK = 0, 1, 2
known = {}   # word -> (kind, freq); a lower kind wins, then the higher frequency


def add(word, kind, freq):
    prev = known.get(word)
    if prev is None or kind < prev[0] or (kind == prev[0] and freq > prev[1]):
        known[word] = (kind, freq)


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
            freq = min(255, max(0, int(fields.get("f", "0") or 0)))
            flags = fields.get("flags", "")
            offensive = "offensive" in flags or fields.get("possibly_offensive") == "true"
            nonword = "nonword" in flags or fields.get("not_a_word") == "true"
            lw = word.lower()
            target = (with_targets and word == lw and re.fullmatch(r"[a-z]+", word) is not None
                      and not offensive and not nonword and freq > 0)
            add(lw, TARGET if target else KNOWN, freq)
            if "'" in lw or "’" in lw:
                stripped = lw.replace("'", "").replace("’", "")
                # Contractions and possessives typed without the apostrophe are meant ("dont",
                # "kates"); other capitalized forms ("O'Day" -> "oday") are mostly collisions
                # with typos of other words.
                possessive = re.search(r"['’]s$", word) is not None
                add(stripped, KNOWN if word == lw or possessive else WEAK, freq)


out = sys.argv[2]
read(sys.argv[1], True)
for extra in sys.argv[3:]:
    read(extra, False)

lines = []
for w, (kind, fq) in known.items():
    if not w or any(c in w for c in "\n\r\t"):
        continue
    lines.append((w.encode("utf-8"), w, kind, fq))
lines.sort()
counts = [0, 0, 0]
with open(out, "w", encoding="utf-8", newline="\n") as f:
    for _, w, kind, fq in lines:
        f.write(f"{w}\t{('', '=', '~')[kind]}{fq}\n")
        counts[kind] += 1
print(f"{len(lines)} words: {counts[0]} correction targets, {counts[1]} known, {counts[2]} weak")
