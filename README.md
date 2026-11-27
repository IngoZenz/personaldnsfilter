# personalDNSfilter

personalDNSfilter is an open-source DNS filter proxy for Android, Linux, macOS, and Windows that blocks tracking, telemetry, malware, and ad-serving domains using configurable host blocklists, wildcard/CNAME inspection, custom IP mappings, and upstream UDP, DoT (DNS-over-TLS), and DoH (HTTP/1.1 & HTTP/2) resolvers.

See [zenz-solutions.de/personaldnsfilter](https://www.zenz-solutions.de/personaldnsfilter) for project details and documentation.

[<img src="https://f-droid.org/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/dnsfilter.android/)

## Project Structure

- `:app` — Android application (VPN service, root/proxy mode, Quick Settings tile, UI, Android unit tests)
- `:desktop` — Standalone Java 8+ JAR (`personalDNSfilter.jar`, entry point `dnsfilter.DNSFilterProxy`) and core JVM test suite
- `full_package/` — Helper scripts and `systemd` unit files for Linux and Windows desktop setups

## Building & Testing

### Desktop JAR & Core Unit Tests (no Android SDK required)

```bash
./gradlew :desktop:test :desktop:jar
```

The runnable JAR is output to `desktop/build/libs/personalDNSfilter.jar` and can be started with:

```bash
java -jar desktop/build/libs/personalDNSfilter.jar
```

### Android App & Unit Tests (requires Android SDK)

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## License

personalDNSfilter is licensed under the GNU General Public License v2.0 or later; see [`LICENSE`](LICENSE). Third-party asset notices are recorded in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
