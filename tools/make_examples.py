#!/usr/bin/env python3
"""Generate committed example fingerprints under datasets/examples/.

The examples are synthetic (hand-built observations, no real device data) and
exist so the tooling and reports have reviewable, schema-valid fixtures without
needing field captures. Regenerate with:

    python3 tools/make_examples.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from analysis.canonical import fingerprint_sha256  # noqa: E402
from analysis.validate import COLLECTOR_SECTIONS  # noqa: E402

OUT_DIR = ROOT / "datasets" / "examples"

MARKERS = [
    "qemu",
    "ranchu",
    "goldfish",
    "houdini",
    "waydroid",
    "anbox",
    "redroid",
    "virtualbox",
    "vbox",
    "genymotion",
    "lxc",
    "cuttlefish",
]


def section(
    name: str,
    status: str = "available",
    normalized: dict | None = None,
    raw: dict | None = None,
    timestamp: str = "2026-01-15T10:00:00Z",
    errors: list | None = None,
    warnings: list | None = None,
) -> dict:
    return {
        "collector": name,
        "timestamp": timestamp,
        "status": status,
        "errors": errors or [],
        "warnings": warnings or [],
        "data": {"raw": raw or {}, "normalized": normalized or {}},
    }


def markers(present: list[str]) -> dict:
    return {name: name in present for name in MARKERS}


def document(label: str, sections: dict[str, dict], timestamp: str) -> dict:
    doc: dict = {
        "schema_version": "1.0",
        "collector_version": "0.1.0",
        "timestamp": timestamp,
        "environment": {"label": label},
    }
    for name in COLLECTOR_SECTIONS:
        doc[name] = sections.get(name) or section(name, timestamp=timestamp)
    doc["collector_status_summary"] = {name: doc[name]["status"] for name in COLLECTOR_SECTIONS}
    doc["fingerprint_sha256"] = fingerprint_sha256(doc)
    return doc


def physical() -> dict:
    ts = "2026-01-15T10:00:00Z"
    return document(
        "physical",
        {
            "build": section(
                "build",
                normalized={
                    "manufacturer": "Google",
                    "brand": "google",
                    "model": "Pixel 7",
                    "device": "panther",
                    "product": "panther",
                    "hardware": "panther",
                    "board": "gs201",
                    "android_version": "14",
                    "sdk_int": 34,
                    "security_patch_level": "2025-11-05",
                    "type": "user",
                },
                timestamp=ts,
            ),
            "abi": section(
                "abi",
                normalized={
                    "supported_abis": ["arm64-v8a", "armeabi-v7a", "armeabi"],
                    "supported_64_bit_abis": ["arm64-v8a"],
                    "supported_32_bit_abis": ["armeabi-v7a", "armeabi"],
                    "runtime_os_arch": "aarch64",
                },
                timestamp=ts,
            ),
            "cpu": section(
                "cpu",
                normalized={
                    "cores": 8,
                    "model_name": "ARMv8 Processor rev 1 (aarch64)",
                    "features": ["asimd", "aes", "pmull", "sha1", "sha2"],
                },
                timestamp=ts,
            ),
            "gpu": section(
                "gpu",
                normalized={"vendor": "Google", "renderer": "Mali-G710", "version": "OpenGL ES 3.2"},
                timestamp=ts,
            ),
            "vulkan": section("vulkan", normalized={"version": "1.3.0"}, timestamp=ts),
            "kernel": section(
                "kernel",
                normalized={"kernel_release": "5.15.148-android14-11-g2b4", "kernel_version": "5.15.148"},
                timestamp=ts,
            ),
            "sensors": section(
                "sensors",
                normalized={"count": 15, "types": ["accelerometer", "gyroscope", "proximity"]},
                timestamp=ts,
            ),
            "telephony": section(
                "telephony",
                normalized={"sim_state": "ready", "phone_type": "gsm", "network_type": "lte"},
                timestamp=ts,
            ),
            "security": section(
                "security",
                normalized={"selinux_mode": "enforcing", "debuggable": False, "adb_enabled": True},
                timestamp=ts,
            ),
            "environment_signals": section(
                "environment_signals",
                normalized={"markers": markers([]), "cgroup_readable": True, "cmdline_readable": True},
                raw={"cmdline": "console=ttyMSM0,115200n8 androidboot.hardware=qcom"},
                timestamp=ts,
            ),
        },
        ts,
    )


def waydroid() -> dict:
    ts = "2026-01-16T11:30:00Z"
    return document(
        "waydroid",
        {
            "build": section(
                "build",
                normalized={
                    "manufacturer": "Google",
                    "brand": "google",
                    "model": "sdk_gphone64_x86_64",
                    "device": "emu64xa",
                    "product": "sdk_gphone64_x86_64",
                    "hardware": "ranchu",
                    "board": "goldfish_x86_64",
                    "android_version": "13",
                    "sdk_int": 33,
                    "type": "userdebug",
                },
                timestamp=ts,
            ),
            "abi": section(
                "abi",
                normalized={
                    "supported_abis": ["x86_64", "arm64-v8a", "armeabi-v7a", "armeabi"],
                    "supported_64_bit_abis": ["x86_64", "arm64-v8a"],
                    "runtime_os_arch": "amd64",
                    "runtime_java_vm": "OpenJDK 64-Bit Server VM",
                },
                timestamp=ts,
            ),
            "cpu": section(
                "cpu",
                normalized={
                    "cores": 8,
                    "model_name": "AMD Ryzen 7 5800X 8-Core Processor",
                    "features": ["sse2", "sse4_1", "sse4_2", "avx", "avx2"],
                },
                timestamp=ts,
            ),
            "gpu": section(
                "gpu",
                normalized={"vendor": "Mesa", "renderer": "llvmpipe (LLVM 15.0.7, 256 bits)", "version": "OpenGL ES 3.2"},
                timestamp=ts,
            ),
            "vulkan": section(
                "vulkan",
                status="unavailable",
                timestamp=ts,
                warnings=["no Vulkan device exposed"],
            ),
            "kernel": section(
                "kernel",
                normalized={"kernel_release": "6.8.0-51-generic", "kernel_version": "6.8.0"},
                timestamp=ts,
            ),
            "sensors": section(
                "sensors",
                status="unavailable",
                normalized={"count": 0, "types": []},
                timestamp=ts,
            ),
            "security": section(
                "security",
                normalized={"selinux_mode": "enforcing", "debuggable": True, "adb_enabled": True},
                timestamp=ts,
            ),
            "environment_signals": section(
                "environment_signals",
                normalized={
                    "markers": markers(["waydroid", "lxc"]),
                    "props": {
                        "ro.hardware.audio.primary": "tinyhal",
                        "ro.build.characteristics": "tablet",
                    },
                    "cgroup_readable": True,
                    "cmdline_readable": True,
                },
                raw={
                    "cmdline": "root=/dev/mapper/android--waydroid--root ro quiet",
                    "cgroup": "0::/lxc/waydroid",
                },
                timestamp=ts,
            ),
        },
        ts,
    )


def kvm_emulator() -> dict:
    ts = "2026-01-17T09:15:00Z"
    return document(
        "kvm",
        {
            "build": section(
                "build",
                normalized={
                    "manufacturer": "Google",
                    "brand": "google",
                    "model": "sdk_gphone64_x86_64",
                    "device": "emu64xa",
                    "product": "sdk_gphone64_x86_64",
                    "hardware": "ranchu",
                    "board": "goldfish_x86_64",
                    "android_version": "14",
                    "sdk_int": 34,
                    "type": "userdebug",
                },
                timestamp=ts,
            ),
            "abi": section(
                "abi",
                normalized={
                    "supported_abis": ["x86_64", "arm64-v8a", "armeabi-v7a"],
                    "supported_64_bit_abis": ["x86_64", "arm64-v8a"],
                    "runtime_os_arch": "amd64",
                },
                timestamp=ts,
            ),
            "cpu": section(
                "cpu",
                normalized={
                    "cores": 4,
                    "model_name": "Intel(R) Core(TM) i7-12700H",
                    "features": ["sse2", "sse4_1", "sse4_2", "avx", "avx2"],
                },
                timestamp=ts,
            ),
            "gpu": section(
                "gpu",
                normalized={"vendor": "Google Inc.", "renderer": "Android Emulator OpenGL ES Translator (llvmpipe)", "version": "OpenGL ES 3.2"},
                timestamp=ts,
            ),
            "vulkan": section("vulkan", normalized={"version": "1.3.231"}, timestamp=ts),
            "kernel": section(
                "kernel",
                normalized={"kernel_release": "5.15.153-android14-11-g4f0", "kernel_version": "5.15.153"},
                timestamp=ts,
            ),
            "sensors": section(
                "sensors",
                normalized={"count": 12, "types": ["accelerometer", "gyroscope"]},
                timestamp=ts,
            ),
            "telephony": section(
                "telephony",
                normalized={"sim_state": "ready", "phone_type": "gsm", "network_type": "lte"},
                timestamp=ts,
            ),
            "security": section(
                "security",
                normalized={"selinux_mode": "enforcing", "debuggable": True, "adb_enabled": True},
                timestamp=ts,
            ),
            "environment_signals": section(
                "environment_signals",
                normalized={
                    "markers": markers(["qemu", "ranchu", "goldfish"]),
                    "props": {
                        "ro.kernel.qemu": "1",
                        "ro.hardware.egl": "emulation",
                        "ro.build.characteristics": "emulator",
                    },
                    "cgroup_readable": True,
                    "cmdline_readable": True,
                },
                raw={"cmdline": "androidboot.hardware=ranchu androidboot.qemu=1"},
                timestamp=ts,
            ),
        },
        ts,
    )


EXAMPLES = {
    "example-physical.json": physical,
    "example-waydroid.json": waydroid,
    "example-kvm-emulator.json": kvm_emulator,
}


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, builder in EXAMPLES.items():
        path = OUT_DIR / name
        doc = builder()
        path.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"wrote {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
