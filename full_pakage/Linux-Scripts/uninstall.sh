#!/bin/bash

# Check sudo/root permission

if [[ $EUID > 0 ]]; then
	echo "Please run as root/sudo"
	exit 1
fi

# Uninstall main jar file
rm -f -v /usr/local/lib/personalDNSfilter/personalDNSfilter.jar
rmdir -v /usr/local/lib/personalDNSfilter
# Empty working directory
rm -vrf  /var/lib/personalDNSfilter
# Remove systemd service file
rm -f -v /etc/systemd/system/personalDNSfilter.service

echo "Uninstalled from system."
