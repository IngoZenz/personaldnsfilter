#############################################################################
##     This program is distributed in the hope that it will be useful,     ##
##     but WITHOUT ANY WARRANTY; without even the implied warranty of      ##
##           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.          ##
##           See the GNU General Public License for more details.          ##
######                                                                 ######
##                                                                         ##
##               This is a DNS Filter Proxy written in Java.               ##
##        It can be used for filtering ads and other unwished hosts        ##
##                      based on Host Name filtering.                      ##
##     Please see dnsfilter.conf for settings and their documentation.     ##
##                                                                         ##
#############################################################################
#                                                                           #
#                NOTE! These scripts are EXPERIMENTAL ONLY!                 # 
#                  Only use in case you are knowledgable!                   #
#                                                                           #
############################################################################# 

*****************************************************************************
*************** Setup and Run personalDNSfilter on Linux OS ***************
*****************************************************************************

- You need to have Java installed:
  => If you have not installed any Java version yet, download and install latest OpenJDK (or only JRE to be small) from your distrobution package manager:
     => For Fedora/Red Hat/CentOS: sudo dnf install java-latest-openjdk-headless
     => For Debian: sudo apt install default-jre-headless
     => For others: Try to search OpenJDK via the package manger on your favourite distro.
  => Note: You always can update to the latest Java version manually, by simply downloading and installing the latest release at https://adoptopenjdk.net/releases.html

- Download personalDNSfilter full package (zip file) from: https://www.zenz-solutions.de/personaldnsfilter
- Extract the zip file anywhere you like, and open the extracted folder.
- Run "sudo sh start.sh" to execute personalDNSfilter for first time (to create "dnsfilter.conf" file), then close it.
- If you want, configure the "dnsfilter.conf" to your liking.
  => If you also use android version of the app on your phone, you can simply copy all things (including settings) from personaldnsfilter folder from your phone to your PC.

- Set pDNSf local DNS address at your system, using one of the following methods:
  => A) GUI via NetworkManager : Set DNS to 127.0.0.1 on desired interface or connection.
  => B) Manually: Depending on your distro, it could be done via either editing "/etc/resolv.conf", "/etc/resolvconf.conf" or "resolvectl" utility.

- Run "sudo sh start.sh" to Execute personalDNSfilter.
- Open some sites. if they appear in the command window, then everything is working.

- To automatically start personalDNSfilter at Linux startup:
  => Run "sudo bash install.sh" to install components into proper locations in system.
       => Require bash and systemd. 
       => Note: The script has only been tested on Fedora and Debian. Hence it is still in pre-Alpha status and may not work properly.
  => Execute "sudo systemd daemon-reload" to detect the installed service file.
  => Use "sudo systemd enable --now personalDNSfilter" to run the service and enable auto start.
  => Now you can use "sudo systemctl status personalDNSfilter" to inspect or "sudo journalctl -u personalDNSfilter -f" to examine the logs, like other services.
- Done!

- Special note if running as the primary DNS resolver in network:
 => By the nature of DNS services, the running logs from personalDNSfilter could be HUGE. Hence the size of "/var/log/syslog" could grow tremendously. To remedy this:
       => Disable log put by directing output to "/dev/null", you can still monitor using mobile app remotely if configured so.
       => Add an override to systemd unit file: "sudo systemctl edit personalDNSfilter"
       => Add a "[Service]" section containing "StandardOutput=journal".
       => Reload and restart the personalDNSfilter: "sudo systemctl daemon-reload && sudo systemctl restart personalDNSfilter"

- To stop automatically start and even uninstall from system locations:
 => Use "sudo systemd disable --now personalDNSfilter" to stop the service and disable auto start.
 => Run "sudo bash uninstall.sh" to uninstall components from various locations in system.
       => Above action will also remove configuartion files and logs that related with personalDNSfilter. Backup first if you still need them.
       => Note: The script has only been tested on Fedora and Debian. Hence it is still in pre-Alpha status and may not work properly.

For further documentation refer to http://www.zenz-solutions.de/personaldnsfilter