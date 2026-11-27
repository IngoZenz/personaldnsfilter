@echo off
cd /d "%~dp0"
java -classpath .\personalDNSfilter.jar dnsfilter.DNSFilterProxy %*
