#!/usr/bin/env bash
set -euo pipefail

# Check availability of Java
JAVA_BIN="/usr/bin/java"
if ! [ -x "$JAVA_BIN" ]; then
	JAVA_BIN="$(command -v java || true)"
fi
if [ -z "$JAVA_BIN" ] || ! [ -x "$JAVA_BIN" ]; then
	echo "Java might not be installed yet. Use your package manager to install OpenJDK JRE first."
	exit 1
fi

# Check root permission
if [ "$(id -u)" -ne 0 ]; then
	echo "Please run as root or use sudo."
	exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SEL_FLAG=""
if command -v selinuxenabled >/dev/null 2>&1 && selinuxenabled; then
	SEL_FLAG="-Z"
fi

if [ ! -f "$SCRIPT_DIR/../personalDNSfilter.jar" ]; then
	echo "Missing $SCRIPT_DIR/../personalDNSfilter.jar"
	exit 1
fi

# Install main jar file
install -d -v /usr/local/lib/personalDNSfilter/
install $SEL_FLAG -v -m 644 -t /usr/local/lib/personalDNSfilter/ "$SCRIPT_DIR/../personalDNSfilter.jar"

# Create working directory
install -d -v /var/lib/personalDNSfilter/

# Copy dnsfilter.conf to working directory if present
if [ -f "$SCRIPT_DIR/../dnsfilter.conf" ]; then
	install $SEL_FLAG -v -m 644 -t /var/lib/personalDNSfilter/ "$SCRIPT_DIR/../dnsfilter.conf"
fi

# Install systemd service file (substitute resolved java path if non-default)
install $SEL_FLAG -v -m 644 "$SCRIPT_DIR/personalDNSfilter.service" /etc/systemd/system/personalDNSfilter.service
if [ "$JAVA_BIN" != "/usr/bin/java" ]; then
	sed -i "s|/usr/bin/java|$JAVA_BIN|g" /etc/systemd/system/personalDNSfilter.service
fi

# Finish
echo "Installation finished. Run 'sudo systemctl daemon-reload && sudo systemctl enable --now personalDNSfilter'."
