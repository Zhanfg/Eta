import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import warnings
import zipfile

spec = importlib.util.spec_from_file_location(
    "verify_workbuddy_apk",
    Path(__file__).resolve().parents[1] / "verify_workbuddy_apk.py",
)
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


def elf(interpreter=b"/system/bin/linker64\x00"):
    ident = b"\x7fELF\x02\x01\x01" + bytes(9)
    size = 64 + 2 * 56 + len(interpreter)
    header = struct.pack(
        "<16sHHIQQQIHHHHHH", ident, 3, 183, 1, 0, 64, 0, 0, 64, 56, 2, 0, 0, 0,
    )
    load = struct.pack("<IIQQQQQQ", 1, 5, 0, 0, 0, size, size, 4096)
    interp = struct.pack(
        "<IIQQQQQQ", 3, 4, 176, 0, 0, len(interpreter), len(interpreter), 1,
    )
    return header + load + interp + interpreter


class CoreVerificationTest(unittest.TestCase):
    def test_accepts_complete_android_pie(self):
        verifier.verify_elf(elf())

    def test_rejects_wrong_class_endianness_type_and_machine(self):
        for offset, value in [(4, 1), (5, 2), (16, 2), (18, 62)]:
            with self.subTest(offset=offset):
                data = bytearray(elf())
                data[offset] = value
                with self.assertRaises(ValueError):
                    verifier.verify_elf(data)

    def test_rejects_truncated_header_table_or_segment(self):
        for size in [4, 63, 100, len(elf()) - 1]:
            with self.subTest(size=size):
                with self.assertRaises(ValueError):
                    verifier.verify_elf(elf()[:size])

    def test_interpreter_must_be_a_real_program_header(self):
        data = bytearray(elf())
        struct.pack_into("<I", data, 120, 0)
        with self.assertRaises(ValueError):
            verifier.verify_elf(data)

    def test_rejects_non_android_interpreter_and_non_executable_load(self):
        with self.assertRaises(ValueError):
            verifier.verify_elf(elf(b"/lib/ld-linux-aarch64.so.1\x00"))
        data = bytearray(elf())
        struct.pack_into("<I", data, 68, 4)
        with self.assertRaises(ValueError):
            verifier.verify_elf(data)

    def test_packaged_binary_must_match_reference_exactly(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            reference = root / "core"
            reference.write_bytes(elf())
            apk = root / "app.apk"
            name = "lib/arm64-v8a/libwbcore.so"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(name, elf())
            self.assertEqual(64, len(verifier.verify_apk(apk, reference)))
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(name, elf() + b"different-build")
            with self.assertRaises(ValueError):
                verifier.verify_apk(apk, reference)

    def test_rejects_absent_or_duplicate_native_entries(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            reference = root / "core"
            reference.write_bytes(elf())
            apk = root / "app.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", b"synthetic")
            with self.assertRaises(ValueError):
                verifier.verify_apk(apk, reference)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr("lib/arm64-v8a/libwbcore.so", elf())
                    archive.writestr("lib/arm64-v8a/libwbcore.so", elf())
            with self.assertRaises(ValueError):
                verifier.verify_apk(apk, reference)


if __name__ == "__main__":
    unittest.main()
