# Security policy

## Supported versions

iCode is in early development. Only the latest release on
[GitHub Releases](https://github.com/itzmejanak/iCode/releases) gets security fixes.

## Reporting a vulnerability

Please do not open a public issue for security problems.

Report it privately through
[GitHub Security Advisories](https://github.com/itzmejanak/iCode/security/advisories/new). Include:

- the iCode version, Android version and device
- what an attacker could do, and the steps to reproduce it
- any proof-of-concept or logs (remove keys, tokens and personal data first)

You'll get an acknowledgement as soon as the maintainer sees the report. A fix and an advisory follow
once the issue is confirmed.

## Scope

In scope: the iCode app, its native runtime loader, its handling of Git credentials, SSH/GPG keys and
AI provider keys, and its requests to the iCode website.

Out of scope: vulnerabilities in third-party tools you install inside the Linux runtime, and in AI
providers' own services.
