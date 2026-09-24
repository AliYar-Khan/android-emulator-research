# Observations

> Field observation log. One block per capture; commit this file alongside
> the captures in `datasets/fingerprints/` when a campaign is recorded.

**Status: awaiting field captures.** The framework, tooling, and example
fixtures are complete; no real-environment captures have been recorded yet.
Committed fixtures under `datasets/examples/` are synthetic and clearly
named `example-*`; they are tooling test data, not observations.

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

(none yet)
