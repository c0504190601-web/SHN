#!/usr/bin/env python3
"""Replace the first MediaTek boot logo while preserving all other images.
Usage: python make_zing_logo.py original_logo.bin zing_boot_480x640.png output_logo.bin
Requires: Pillow. Verify the result on the target device before flashing.
"""
import sys, struct, zlib
from PIL import Image
source, picture, target = sys.argv[1:4]
blob = bytearray(open(source, "rb").read())
assert blob[:4] == bytes.fromhex("88168858"), "Not a recognized MTK partition header"
start = 512
count, total = struct.unpack_from("<II", blob, start)
assert 1 <= count <= 100 and 0 < total <= len(blob)-start
offsets = list(struct.unpack_from("<" + "I"*count, blob, start+8))
assert offsets == sorted(offsets) and offsets[0] >= 8+4*count
old = zlib.decompress(blob[start+offsets[0]:start+offsets[1]])
im = Image.open(picture).convert("RGBA")
assert im.size == (480,640) and len(old) == len(im.tobytes()), "Wrong image dimensions"
new = zlib.compress(im.tobytes(), 9)
delta = len(new) - (offsets[1]-offsets[0])
newtotal = total + delta
assert newtotal <= len(blob)-start, "New logo exceeds partition"
header = bytearray(blob[:start])
struct.pack_into("<I", header, 4, newtotal)
table = bytearray(blob[start:start+offsets[0]])
struct.pack_into("<I", table, 4, newtotal)
for i in range(1,count):
    struct.pack_into("<I", table, 8+4*i, offsets[i]+delta)
out = header + table + new + blob[start+offsets[1]:start+total] + blob[start+total:]
if len(out) > len(blob): raise ValueError("Not enough space")
out += bytes([255])*(len(blob)-len(out))
assert len(out) == len(blob)
open(target,"wb").write(out)
print("Wrote",target)
