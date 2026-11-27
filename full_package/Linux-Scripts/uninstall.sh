#!/usr/bin/env bash
set -euo pipefail

# Check root permission
if [ "$(id -u)" -ne 0 ]; then
	echo "Please run as root/sudo"
	exit 1
fi

# Stop and disable service if active
if command -v systemctl >/dev/null 2>&1; then
	systemctl disable --now personalDNSfilter >/dev/null 2>&1 || true
fi

# Uninstall main jar file
rm -f -v /usr/local/lib/personalDNSfilter/personalDNSfilter.jar
if [ -d /usr/local/lib/personalDNSfilter ]; then
	rmdir -v /usr/local/lib/personalDNSfilter || true
fi

# Empty working directory
rm -vrf /var/lib/personalDNSfilter

# Remove systemd service file
rm -f -v /etc/systemd/system/personalDNSfilter.service
if command -v systemctl >/dev/null 2>&1; then
	systemctl daemon-reload || true
fi

echo "Uninstalled from system."
