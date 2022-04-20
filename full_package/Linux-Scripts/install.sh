#!/bin/bash

# Check availbility of java

if ! [ -x /usr/bin/java ]; then
	echo "Java might not be installed yet. Use your package manager to install OpenJDK JRE first."
	exit 1
fi

# Check sudo/root permission

if [[ $EUID > 0 ]]; then
	echo "Please run as root or use sudo."
	exit 1
fi

# Install main jar file
install -d -v /usr/local/lib/personalDNSfilter/
install -Z -v -m 644 -t /usr/local/lib/personalDNSfilter/ ../personalDNSfilter.jar
# Create working directory
install -d -v /var/lib/personalDNSfilter/
# Copy dnsfilter.conf to working directory if exist
if [ -w ../dnsfilter.conf ]; then
	install -Z -v -m 644 -t /var/lib/personalDNSfilter/ ../dnsfilter.conf
fi
# Install systemd service file
install -Z -v -m 644 -t /etc/systemd/system/ personalDNSfilter.service

# Finish
echo "Finish installation. Check README file for additional info on configuration."
