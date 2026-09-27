# Observations

> Field observation log. One block per capture; commit this file alongside
> the captures in `datasets/fingerprints/` when a campaign is recorded.
> (Capture JSON files themselves are not tracked — see `.gitignore`.)

## Recording template

Copy this block per capture:

```markdown
### <label> — <YYYY-MM-DD>

- label:            <physical | waydroid | kvm | aosp | commercial | other>
- environment:      <device model + ROM/build id, or distro + image version>
- host kernel:      <if VM/container: uname -r of the host>
- collector commit: <git rev-parse --short HEAD>
- capture command:  scripts/collect.sh <label>
- output:           datasets/fingerprints/<label>-<UTC>.json
- operator:         <initials>
- date (UTC):       <YYYY-MM-DDTHH:MM:SSZ>

Validation:  <paste analysis.validate summary line>
Anomalies:   <permission_denied sections, collection_error details,
              timeouts, anything unexpected — or "none">

Notes:       <free text: images used, flags, host config worth reproducing>
```

## Campaign log

### physical — 2026-09-27

- label:            physical
- environment:      Vivo V2511, Android 16 (SDK 36),
                    build `vivo/V2511/V2511:16/BP2A.250605.031.A3_V000L1/...:user/release-keys`
- host kernel:      n/a (physical device)
- collector commit: 6b85756 (working tree identical)
- capture commands: `scripts/collect.sh physical` (×2)
- outputs:          `datasets/fingerprints/physical-20260927T143253Z.json`,
                    `datasets/fingerprints/physical-20260927T143405Z.json`
- operator:         _fill in_
- date (UTC):       2026-09-27T14:32:53Z and 2026-09-27T14:34:05Z

Validation:  both files `OK (0 warning(s))`, hashes verified; all 16 sections
             status `available`.
Anomalies:   No `collection_error`, no `permission_denied`. Android 16 hides
             several world-readable surfaces from apps — recorded as section
             warnings (not errors): `/proc/version`, `/proc/sys/kernel/osrelease`,
             `/proc/loadavg`, `/proc/uptime`, `/proc/cmdline`, `/proc/net/dev`,
             `/sys/devices/system/node/possible`, `/sys/devices/virtual/dmi/id/*`,
             `/sys/fs/selinux/enforce`; plus `display: hidden ColorManager API`
             and `cpu: model/cpuinfo fields absent`.

Notes:       Device hardware: Qualcomm Adreno (TM) 722 GPU (OpenGL ES 3.2),
             arm64-v8a, 8-core ARM. Verified boot `green`, flash locked,
             `release-keys`. SIM present (PK). Environment markers: none
             detected (consistent with a physical device).

             Stability check (two captures ~70 s apart): `analysis.diff`
             reported only 18 leaf differences confined to `proc` and
             `storage` — memory/swap counters and free-block counts. All
             other 14 sections were structurally identical; `fingerprint_sha256`
             differs between runs as predicted (dynamic values are hashed).
