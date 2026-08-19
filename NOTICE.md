# NOTICE — Irium licensing scope

This file clarifies how the Elastic License 2.0 applies to the Irium project.

## What is open

All of the following are available under the terms of the Elastic License 2.0
(see [LICENSE](LICENSE)): you may use, study, modify, and redistribute them,
including commercially:

- the Irium server plugin (open parts),
- the Microsoft Store companion app (free),
- the Irium API and tooling used to write Irium-safe mods,
- documentation, research documents, and assets,
- any future components explicitly marked as open.

## What is closed

The **authentication component** of the Irium plugin is **not** distributed as
source code. It is shipped as a closed binary.

This component verifies that a server is enrolled before the platform streams
any module to its players. It is the enforcement point of the trust chain:
**un-enrolled server = no streaming.**

## Why

Server enrollment is the mechanism that keeps Irium safe for players and fair
for operators. Enrollment is handled remotely by the Irium platform:

- test and small servers can be enrolled for free, on validation;
- large servers enroll under paid plans, which include support and services;
- servers can be added or removed remotely at any time — no code change,
  no update required.

The license (Elastic License 2.0) explicitly forbids moving, changing,
disabling, or circumventing the license key functionality, and removing or
obscuring functionality protected by it — **including in modified copies** of
the open parts.

## Practical summary

| | |
|---|---|
| Read, learn from, modify the open code | allowed |
| Redistribute modified versions of the open parts | allowed |
| Use Irium on an enrolled server | allowed |
| Build Irium-safe mods with the Irium API | allowed |
| Ship a modified build with the authentication component removed, replaced, or bypassed | forbidden |
| Self-enroll a server without platform validation | forbidden |

Questions about licensing or enrollment: open an issue on this repository.
