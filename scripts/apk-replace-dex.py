#!/usr/bin/env python3
"""Replace one stored dex in a system APK and keep its signing block where it is.

A system app's APK can be bound over in the guest with one dex changed only if
PackageManager still reads the same certificate from it: it skips digest
verification for APKs on the system partitions and takes the certificates as
they are (ApkSignatureVerifier.unsafeGetCertsWithoutVerification), but it has to
find the APK Signing Block right in front of the central directory, and a zip
tool rewriting the archive drops it. So: every local entry stays byte for byte
where it is, the new dex is appended after the last one (stored, 4-byte aligned
like zipalign, so ART maps it directly), then the unchanged signing block, then
the central directory with that one record's CRC, sizes and offset changed. The
old dex stays in the file, unreferenced. make-fold8-coloros.py's repack does the
same for the Find N6 SystemUI on the Z Fold8 (n6-f8, shipped in 0.4.14).

An APK signed with a verity digest (VERITY_CHUNKED_SHA256 in its v2/v3 signer)
cannot have its signing block moved: PackageManager checks the source length
recorded in that digest against where the block starts even when it skips the
digests themselves, and refuses the APK ("Failed to collect certificates ...
using APK Signature Scheme v3"). There the new dex goes into the old one's slot,
deflated (a reassembled dex is a little larger than R8's, deflated it is about
a third), and every other byte of the file stays where it was; the rest of the
old slot is left as it is, unreferenced. ART unpacks that one dex when it loads.

The APK's own odex/vdex no longer match that dex's checksum; ART then runs the
APK's dex as it is (JIT), which is slower to start but correct.

Usage: apk-replace-dex.py <in.apk> <entry name, e.g. classes10.dex> <new.dex> <out.apk>
"""
import struct
import sys
import zlib

EOCD_SIG = b"PK\x05\x06"
CDH_SIG = b"PK\x01\x02"
LFH_SIG = b"PK\x03\x04"
SIG_MAGIC = b"APK Sig Block 42"


VERITY_ALGOS = (0x0421, 0x0423, 0x0425)


def lp(b, o):
    n = struct.unpack_from("<I", b, o)[0]
    return b[o + 4:o + 4 + n], o + 4 + n


def has_verity(block):
    """Whether a v2 or v3 signer of this APK Signing Block carries a verity digest."""
    off = 8
    while off < len(block) - 24:
        size, bid = struct.unpack_from("<QI", block, off)
        val = block[off + 12:off + 8 + size]
        off += 8 + size
        if bid not in (0x7109871A, 0xF05368C0):
            continue
        signers, _ = lp(val, 0)
        o = 0
        while o < len(signers):
            signer, o = lp(signers, o)
            signed, _ = lp(signer, 0)
            digests, _ = lp(signed, 0)
            d = 0
            while d < len(digests):
                item, d = lp(digests, d)
                if struct.unpack_from("<I", item, 0)[0] in VERITY_ALGOS:
                    return True
    return False


def main():
    src, name, dex_path, out = sys.argv[1:5]
    data = open(src, "rb").read()
    new = open(dex_path, "rb").read()
    if new[:4] != b"dex\n":
        sys.exit(f"{dex_path} is not a dex file")

    eocd = data.rfind(EOCD_SIG, max(0, len(data) - 65557))
    if eocd < 0 or eocd + 22 != len(data):
        sys.exit("no end of central directory record, or the archive has a comment")
    (disk, cd_disk, n_disk, n_total, cd_size, cd_off, clen) = struct.unpack("<HHHHIIH", data[eocd + 4:eocd + 22])
    if disk or cd_disk or n_disk != n_total or n_total == 0xFFFF or cd_off == 0xFFFFFFFF:
        sys.exit("multi-disk or zip64 archive: not handled")
    if cd_off + cd_size != eocd:
        sys.exit("central directory does not end at the end record")
    if data[cd_off - 16:cd_off] != SIG_MAGIC:
        sys.exit("no APK Signing Block in front of the central directory")
    blk_size = struct.unpack("<Q", data[cd_off - 24:cd_off - 16])[0]
    sig_start = cd_off - blk_size - 8
    if struct.unpack("<Q", data[sig_start:sig_start + 8])[0] != blk_size:
        sys.exit("APK Signing Block sizes disagree")

    cd = bytearray(data[cd_off:cd_off + cd_size])
    pos, found, last_end = 0, None, 0
    for _ in range(n_total):
        if cd[pos:pos + 4] != CDH_SIG:
            sys.exit("bad central directory record")
        (method, crc, csize, usize, nlen, xlen, mlen, lho) = struct.unpack(
            "<H4xIIIHHH8xI", bytes(cd[pos + 10:pos + 46]))
        ename = bytes(cd[pos + 46:pos + 46 + nlen]).decode()
        lnlen, lxlen = struct.unpack("<HH", data[lho + 26:lho + 30])
        last_end = max(last_end, lho + 30 + lnlen + lxlen + csize)
        if ename == name:
            if method != 0:
                sys.exit(f"{name} is compressed in {src}; only a stored dex is handled")
            found = (pos, lho)
        pos += 46 + nlen + xlen + mlen
    if found is None:
        sys.exit(f"{name} is not in {src}")
    if last_end > sig_start:
        sys.exit("local entries run into the signing block")

    rec, old_lho = found
    if has_verity(data[sig_start:cd_off]):
        in_place(data, cd, rec, old_lho, name, new, eocd, out)
        return
    old_lfh = data[old_lho:old_lho + 30]
    if old_lfh[:4] != LFH_SIG:
        sys.exit(f"bad local header for {name}")
    version, flags, _method, mtime, mdate = struct.unpack("<HHHHH", old_lfh[4:14])
    bname = name.encode()
    crc = zlib.crc32(new) & 0xFFFFFFFF

    head = data[:sig_start]
    off = len(head)
    if len(new) % 4:
        sys.exit(f"{dex_path} is not a multiple of 4 bytes long")
    # The signing block keeps the alignment it had: an APK signed with a verity
    # digest has it at a multiple of 4096 (apksigner refuses one that is not),
    # so the padding goes in the new entry's extra field
    # and ends the dex where the block must start. A dex is a multiple of 4
    # bytes long, so the dex itself then stays 4-byte aligned for ART.
    align = 4096 if sig_start % 4096 == 0 else 4
    pad = (-(off + 30 + len(bname) + len(new))) % align
    if (off + 30 + len(bname) + pad) % 4:
        sys.exit("cannot align both the dex and the signing block")
    lfh = struct.pack("<4sHHHHHIIIHH", LFH_SIG, version, flags & ~0x8, 0, mtime, mdate,
                      crc, len(new), len(new), len(bname), pad) + bname + b"\0" * pad
    struct.pack_into("<III", cd, rec + 16, crc, len(new), len(new))
    struct.pack_into("<I", cd, rec + 42, off)

    body = head + lfh + new
    new_cd_off = len(body) + blk_size + 8
    eocd_rec = data[eocd:eocd + 16] + struct.pack("<IH", new_cd_off, 0)
    with open(out, "wb") as f:
        f.write(body)
        f.write(data[sig_start:cd_off])
        f.write(bytes(cd))
        f.write(eocd_rec)


def in_place(data, cd, rec, lho, name, new, eocd, out):
    """The new dex deflated into the old entry's slot; nothing else moves."""
    nlen, xlen = struct.unpack_from("<HH", data, lho + 26)
    old_csize = struct.unpack_from("<I", cd, rec + 20)[0]
    start = lho + 30 + nlen + xlen
    co = zlib.compressobj(9, zlib.DEFLATED, -15)
    comp = co.compress(new) + co.flush()
    if len(comp) > old_csize:
        sys.exit("verity-signed APK: the new %s deflated (%d bytes) does not fit the old one's %d; "
                 "the signing block cannot move" % (name, len(comp), old_csize))
    crc = zlib.crc32(new) & 0xFFFFFFFF
    buf = bytearray(data)
    struct.pack_into("<HHH", buf, lho + 4, 20, struct.unpack_from("<H", data, lho + 6)[0] & ~0x8, 8)
    struct.pack_into("<III", buf, lho + 14, crc, len(comp), len(new))
    buf[start:start + len(comp)] = comp
    struct.pack_into("<H", cd, rec + 6, 20)
    struct.pack_into("<H", cd, rec + 10, 8)
    struct.pack_into("<III", cd, rec + 16, crc, len(comp), len(new))
    cd_off = struct.unpack_from("<I", data, eocd + 16)[0]
    buf[cd_off:cd_off + len(cd)] = cd
    assert len(buf) == len(data)
    with open(out, "wb") as f:
        f.write(bytes(buf))


if __name__ == "__main__":
    main()
