# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 0.1.x   | :white_check_mark: |

Older versions (pre-0.1.0) are not supported. Please upgrade to the latest release.

## Reporting a Vulnerability

**Do not report security vulnerabilities through public GitHub issues.**

Please use [GitHub Security Advisories](https://github.com/b3-cognition/b3-meter/security/advisories/new)
to report vulnerabilities privately. This keeps the vulnerability confidential until a patch is available.

Alternatively, send a detailed report to: **security@b3cognition.io** (replace with real address before publishing)

### What to include

- Description of the vulnerability and its potential impact
- Steps to reproduce (proof-of-concept code is welcome)
- Affected versions and components
- Any suggested mitigations

## Response Timeline

| Action | Timeline |
|--------|----------|
| Acknowledgement | Within 72 hours of report |
| Initial assessment | Within 7 days |
| Patch for HIGH/CRITICAL | Within 30 days |
| Patch for MEDIUM | Within 90 days |
| Patch for LOW | Best effort, next minor release |

## Disclosure Policy

We follow a **coordinated disclosure** model:

1. Reporter submits vulnerability privately.
2. We acknowledge and begin investigation.
3. We develop and test a patch.
4. We release the patch as a new version.
5. We publish a security advisory with full details **90 days** after the initial report (or sooner, if the reporter agrees).

We credit reporters by name (or alias) in the security advisory unless they request anonymity.

## Scope

The following are **in scope**:

- Remote code execution via deserialization (XStream, etc.)
- Authentication bypass in the REST API
- JWT token forgery or privilege escalation
- Injection vulnerabilities (SQL, command, LDAP, etc.)
- SSRF via sampler URL configuration
- Exposed sensitive data in logs or API responses

The following are **out of scope**:

- Denial-of-service attacks (b3meter is a load testing tool — DoS is expected behaviour)
- Vulnerabilities requiring physical access to the machine running b3meter
- Vulnerabilities in third-party dependencies that have no available fix
- Social engineering attacks

## Security Design Notes

- JMeter JMX test plans are deserialized via XStream with an explicit allowlist (`XStreamSecurityPolicy`). Unknown classes are denied by default.
- JWT tokens use HS256 with a configurable secret. Rotate the secret to invalidate all existing sessions.
- The web API is not intended to be directly internet-facing without a reverse proxy with authentication.
