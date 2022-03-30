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
*************** Setup and Run personalDNSfilter on Windows OS ***************
*****************************************************************************

- You need to have Java installed:
  => If you have not installed any Java version yet, download and install latest OpenJDK (or only JRE to be small) from: https://adoptopenjdk.net/releases.html
  => If you are not sure which one to choose, use the following direct download links (of OpenJDK15-jre):
     => for x86 PC: https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.1%2B9/OpenJDK15U-jre_x86-32_windows_hotspot_15.0.1_9.msi
     => for x64 PC: https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.1%2B9/OpenJDK15U-jre_x64_windows_hotspot_15.0.1_9.msi
  => Note: You always can update to the latest Java version, by simply downloading and installing the latest release at https://adoptopenjdk.net/releases.html

- Download personalDNSfilter full package (zip file) from: https://www.zenz-solutions.de/personaldnsfilter
- Extract the zip file anywhere you like, and open the extracted folder.
- Run "start.bat" to Execute personalDNSfilter for first time (to create "dnsfilter.conf" file), then close it.
- If you want, configure the "dnsfilter.conf" to your liking.
  => If you also use android version of the app on your phone, you can simply copy all things (including settings) from personaldnsfilter folder from your phone to your PC.

- Set pDNSf local DNS address at your system, using one of the following methods:
  => A) With script (simple): Open "Windows-Scripts" folder and Run "manage-dns-windows.bat" and follow the instructions on the screen.(restoring to default DNS is also available)
     => Note: Although the script has been tested thoroughly, it is still in Alpha version and may not work properly. Please report any issues to Telegram group: https://t.me/pdnsf
  => B) Manually: Set 127.0.0.1 (for IPv4) and ::1 (for IPv6) in your current network adapter's dns settings.

- Run "start.bat" to Execute personalDNSfilter.
- Open some sites. if they appear in the command window, then everything is working.

- To automatically start personalDNSfilter at windows startup (and also to remove auto start):
  => Open "Windows-Scripts" folder and Run "manage-auto-start.bat" and follow the instructions on the screen.
     => Note: Although the script has been tested thoroughly, it is still in Alpha version and may not work properly. Please report any issues to Telegram group: https://t.me/pdnsf
- Done!


For further documentation refer to http://www.zenz-solutions.de/personaldnsfilter