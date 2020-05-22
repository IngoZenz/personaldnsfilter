/*
 PersonalDNSFilter 1.5
 Copyright (C) 2017 Ingo Zenz

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

 Find the latest version at http://www.zenz-solutions.de/personaldnsfilter
 Contact:i.z@gmx.net
 */

package dnsfilter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;

import dnsfilter.remote.RemoteAccessServer;
import util.ExecutionEnvironment;
import util.FileLogger;
import util.Logger;
import util.LoggerInterface;
import util.Utils;


public class DNSFilterManager extends ConfigurationAccess  {

	public static final String VERSION = "1504000";

	private static DNSFilterManager INSTANCE = new DNSFilterManager();

	static public boolean debug;
	static public String WORKDIR = "";
	private static String filterReloadURL;
	private static boolean filterHostsFileRemoveDuplicates;
	private static String filterhostfile;
	private static long filterReloadIntervalDays;
	private static long nextReload;
	private static int okCacheSize = 500;
	private static int filterListCacheSize = 500;
	private static boolean reloadUrlChanged;
	private static String additionalHostsImportTS = "0";
	private static boolean validIndex;
	private static boolean aborted = false;


	private static LoggerInterface TRAFFIC_LOG;

	private static BlockedHosts hostFilter = null;
	private static Hashtable hostsFilterOverRule = null;
	private boolean serverStopped = true;
	private boolean reloading_filter = false;
	private AutoFilterUpdater autoFilterUpdater;

	private static RemoteAccessServer remoteAccessManager ;


	private static String DOWNLOADED_FF_PREFIX= "# Downloaded by personalDNSFilter at: ";


	protected Properties config = null;


	public static  DNSFilterManager getInstance(){
		return INSTANCE;
	}

	private DNSFilterManager() {
		// read Configs etc
	}


	private class AsyncIndexBuilder implements Runnable {

		@Override
		public void run() {
			reloading_filter = true;
			try {
				rebuildIndex();
			} catch (IOException e) {
				Logger.getLogger().logException(e);
			} finally {
				reloading_filter = false;
			}
		}
	}


	private class AutoFilterUpdater implements Runnable {

		private Object monitor;
		boolean stopRequest = false;
		boolean running = false;

		public AutoFilterUpdater() {
			this.monitor =INSTANCE;
		}

		private void waitUntilNextFilterReload() throws InterruptedException {
			// This strange kind of waiting per 10 seconds interval is needed for Android as during device sleep the timer is stopped.
			// This caused the problem that on Android the filter was never updated during runtime but only when restarting the app.
			synchronized (monitor) {
				while (nextReload > System.currentTimeMillis() && !stopRequest)
					monitor.wait(10000);
			}
		}

		public void stop() {
			stopRequest = true;
			synchronized (monitor) {
				monitor.notifyAll();
				while (running)
					try {
						monitor.wait();
					} catch (InterruptedException e) {
						Logger.getLogger().logException(e);
					}
			}
		}


		@Override
		public void run() {

			synchronized (monitor) {

				try {
					running = true;

					int retry = 0;
					long waitTime;

					while (!stopRequest) {

						Logger.getLogger().logLine("DNS Filter: Next filter reload:" + new Date(nextReload));
						try {
							waitUntilNextFilterReload();
						} catch (InterruptedException e) {
							// nothing to do!
						}
						if (stopRequest)
							break;
						try {
							reloading_filter = true;
							Logger.getLogger().logLine("DNS Filter: Reloading hosts filter ...");
							if (updateFilter()) { // otherwise it was aborted
								validIndex = false;
								reloadFilter(false);
								Logger.getLogger().logLine("Reloading hosts filter ... completed!");
							}
							waitTime = filterReloadIntervalDays * 24 * 60 * 60 * 1000;
							nextReload = System.currentTimeMillis() + waitTime;
							retry = 0;

						} catch (Exception e) {
							//Logger.getLogger().logException(e);
							Logger.getLogger().logLine("Cannot update hosts filter file!");
							Logger.getLogger().logLine(e.toString());
							if (retry < 10) {
								if (retry < 5)
									waitTime = 60000;
								else
									waitTime = 3600000; // retry after 1 h

								nextReload = System.currentTimeMillis() + waitTime;
								Logger.getLogger().logLine("Retry at: " + new Date(nextReload));
								retry++;
							} else {
								Logger.getLogger().logLine("Giving up! Reload skipped!");
								waitTime = filterReloadIntervalDays * 24 * 60 * 60 * 1000;
								nextReload = System.currentTimeMillis() + waitTime;
								retry = 0;
							}
						} finally {
							reloading_filter = false;
						}
					}
					Logger.getLogger().logLine("DNS Filter: AutoFilterUpdater stopped!");
				} finally {
					running = false;
					monitor.notifyAll();
				}
			}
		}
	}



	@Override
	public void releaseConfiguration() {
		//nothing to do here
	}

	@Override
	public Properties getConfig() throws IOException {

		try {
			if (config == null) {
				byte[] configBytes = readConfig();
				config = new Properties();
				config.load(new ByteArrayInputStream(configBytes));
			}
			return config;
		} catch (IOException e) {
			Logger.getLogger().logException(e);
			return null;
		}
	}

	@Override
	public byte[] readConfig() throws ConfigurationAccessException{

		File propsFile = new File(WORKDIR + "dnsfilter.conf");
		if (!propsFile.exists()) {
			Logger.getLogger().logLine(propsFile + " not found! - creating default config!");
			createDefaultConfiguration();
			propsFile = new File(WORKDIR + "dnsfilter.conf");
		}
		try {
			InputStream in = new FileInputStream(propsFile);
			byte[] config = Utils.readFully(in,1024);
			in.close();

			// check for additionalHosts.txt
			File f = new File(WORKDIR+"additionalHosts.txt");
			if (!f.exists()) {
				f.createNewFile();
				FileOutputStream fout = new FileOutputStream(f);
				InputStream defIn = ExecutionEnvironment.getEnvironment().getAsset("additionalHosts.txt");
				Utils.copyFully(defIn, fout, true);
			}

			//check versions, in case different merge existing configuration with defaults
			File versionFile = new File(WORKDIR+"VERSION.TXT");
			String vStr = "";
			if (versionFile.exists()) {
				InputStream vin = new FileInputStream(versionFile);
				vStr = new String(Utils.readFully(vin, 100));
				vin.close();
			}
			if (!vStr.equals(DNSFilterManager.VERSION)) {
				//Version Change ==> merge config with new default config
				Logger.getLogger().logLine("Updated version! Previous version:" + vStr + ", current version:" + DNSFilterManager.VERSION);
				createDefaultConfiguration();
				config = mergeAndPersistConfig(config);
			}
			return config;
		} catch (IOException e) {
			Logger.getLogger().logException(e);
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	private byte[] mergeAndPersistConfig(byte[] currentConfigBytes) throws IOException {
		String currentCfgStr = new String(currentConfigBytes);
		boolean filterDisabled = currentCfgStr.indexOf("\n#!!!filterHostsFile =")!=-1;
		if (filterDisabled)
			currentCfgStr = currentCfgStr.replace("\n#!!!filterHostsFile =", "\nfilterHostsFile =");
		Properties currentConfig = new Properties();
		currentConfig.load(new ByteArrayInputStream(currentCfgStr.getBytes()));

		String[] currentKeys = currentConfig.keySet().toArray(new String[0]);
		BufferedReader defCfgReader = new BufferedReader(new InputStreamReader(ExecutionEnvironment.getEnvironment().getAsset("dnsfilter.conf")));
		File mergedConfig = new File(WORKDIR+"dnsfilter.conf");
		FileOutputStream mergedout = new FileOutputStream(mergedConfig);
		String ln = "";

		//hostfile-net discontinued - take over new default filters in case currently defaults are set
		boolean useNewDefaultFilters = currentConfig.getProperty("previousAutoUpdateURL","").trim().equals("https://adaway.org/hosts.txt; https://hosts-file.net/ad_servers.txt; https://hosts-file.net/emd.txt");

		while ((ln = defCfgReader.readLine()) != null) {
			if (!(useNewDefaultFilters && ln.startsWith("filterAutoUpdateURL"))) {
				for (int i = 0; i < currentKeys.length; i++) {
					if (ln.startsWith(currentKeys[i] + " =")) {
						if (currentKeys[i].equals("filterHostsFile") && filterDisabled)
							ln = "#!!!filterHostsFile" + " = " + currentConfig.getProperty(currentKeys[i], "");
						else
							ln = currentKeys[i] + " = " + currentConfig.getProperty(currentKeys[i], "");
					}
				}
			} else Logger.getLogger().logLine("Taking over default configuration: "+ln);

			mergedout.write((ln + "\r\n").getBytes());
		}
		defCfgReader.close();

		//take over custom properties (such as filter overrules) which are not in def config
		Properties defProps = new Properties();
		defProps.load(ExecutionEnvironment.getEnvironment().getAsset("dnsfilter.conf"));
		boolean first = true;
		for (int i = 0; i < currentKeys.length; i++) {
			if (!defProps.containsKey(currentKeys[i])) {
				if (first)
					mergedout.write(("\r\n# Merged custom config from previous config file:\r\n").getBytes());
				first = false;
				ln = currentKeys[i] + " = " + currentConfig.getProperty(currentKeys[i], "");
				mergedout.write((ln + "\r\n").getBytes());
			}
		}
		mergedout.flush();
		mergedout.close();
		Logger.getLogger().logLine("Merged configuration 'dnsfilter.conf' after update to version " + DNSFilterManager.VERSION + "!");
		InputStream in = new FileInputStream(mergedConfig);
		byte[] configBytes = Utils.readFully(in, 1024);
		in.close();

		return configBytes;
	}


	private void createDefaultConfiguration() {
		try {
			File f = new File(WORKDIR+".");
			f.mkdir();

			//dnsfilter.con f
			copyFromAssets("dnsfilter.conf", "dnsfilter.conf");

			//additionalHosts.txt
			if (!new File(WORKDIR+"additionalHosts.txt").exists())
				copyFromAssets("additionalHosts.txt", "additionalHosts.txt");

			//VERSION.TXT
			f = new File(WORKDIR+"VERSION.TXT");
			f.createNewFile();
			OutputStream fout = new FileOutputStream(f);

			fout.write(DNSFilterManager.VERSION.getBytes());

			fout.flush();
			fout.close();

			Logger.getLogger().logLine("Default configuration created successfully!");
		} catch (IOException e) {
			Logger.getLogger().logLine("FAILED creating default Configuration!");
			Logger.getLogger().logException(e);
		}
	}


	@Override
	public void updateConfig(byte[] config) throws IOException {
		try {
			FileOutputStream out = new FileOutputStream(WORKDIR + "dnsfilter.conf");
			out.write(config);
			out.flush();
			out.close();

			this.config.load(new ByteArrayInputStream(config));

			Logger.getLogger().message("Config Changed!\nRestart might be required!");
			//only update in file system / config instance will be updated with next restart
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	@Override
	public byte[] getAdditionalHosts(int limit) throws IOException {
		try {
			File additionalHosts = new File(WORKDIR + "additionalHosts.txt");
			if (additionalHosts.length() > limit)
				return null;

			InputStream in = new FileInputStream(additionalHosts);
			byte[] result = Utils.readFully(in, 1024);
			in.close();
			return result;
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	@Override
	public void updateAdditionalHosts(byte[] bytes) throws IOException {
		try {
			FileOutputStream out = new FileOutputStream(WORKDIR + "additionalHosts.txt");
			out.write(bytes);
			out.flush();
			out.close();
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	@Override
	public void triggerUpdateFilter() {
		if (reloading_filter) {
			Logger.getLogger().logLine("Filter Reload currently running!");
			return;
		}
		if (filterReloadURL != null) {
			synchronized (this) {
				nextReload = 0;
				this.notifyAll();
			}
		} else
			Logger.getLogger().logLine("DNS Filter: Setting 'filterAutoUpdateURL' not configured - cannot update filter!");
	}

	private void copyLocalFile(String from, String to) throws IOException {
		File fromFile = new File(WORKDIR  + from);
		File toFile = new File(WORKDIR  + to);
		Utils.copyFile(fromFile, toFile);
	}

	@Override
	public String[] getAvailableBackups() throws IOException {
		File file = new File(WORKDIR+"backup");
		String[] names = new String[0];
		if (file.exists())
			names = file.list();

		ArrayList<String> result = new ArrayList<String>();

		for(String name : names)
		{
			if (new File(WORKDIR+"backup/" + name).isDirectory())
				result.add(name);
		}
		Collections.sort(result);
		return (String[]) result.toArray(new String[0]);
	}


	@Override
	public void doBackup(String name) throws IOException {
		try {
			copyLocalFile("dnsfilter.conf", "backup/"+name+"/dnsfilter.conf");
			copyLocalFile("additionalHosts.txt", "backup/"+name+"/additionalHosts.txt");
			copyLocalFile("VERSION.TXT", "backup/"+name+"/VERSION.TXT");
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	private void copyFromAssets(String from, String to) throws IOException {

		InputStream defIn = ExecutionEnvironment.getEnvironment().getAsset(from);
		File toFile = new File(("./"+WORKDIR + to));
		toFile.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(toFile);
		Utils.copyFully(defIn, out, true);
	}

	@Override
	public void doRestoreDefaults() throws IOException {

		try {
			if (!canStop())
				throw new IOException("Cannot stop! Pending Operation!");

			stop();
			copyFromAssets("dnsfilter.conf", "dnsfilter.conf");
			copyFromAssets("additionalHosts.txt", "additionalHosts.txt");

			//cleanup hostsfile and index in order to force reload
			String filterHostFile = null;
			if (config != null && ((filterHostFile = config.getProperty("filterHostsFile")) != null)) {
				new File(WORKDIR + filterHostFile).delete();
			}
			init();
			ExecutionEnvironment.getEnvironment().onReload();
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	@Override
	public void doRestore(String name) throws IOException {

		try {
			if (!canStop())
				throw new IOException("Cannot stop! Pending Operation!");

			stop();
			copyLocalFile("backup/"+name+"/dnsfilter.conf", "dnsfilter.conf");
			copyLocalFile("backup/"+name+"/additionalHosts.txt", "additionalHosts.txt");
			copyLocalFile("backup/"+name+"/VERSION.TXT", "VERSION.TXT");

			//cleanup hostsfile and index in order to force reload
			String filterHostFile = null;
			if (config != null && ((filterHostFile = config.getProperty("filterHostsFile")) != null)) {
				new File(WORKDIR + filterHostFile).delete();
			}
			init();
			ExecutionEnvironment.getEnvironment().onReload();
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	@Override
	public void wakeLock()  {
		ExecutionEnvironment.getEnvironment().wakeLock();
	}

	@Override
	public void releaseWakeLock()  {
		ExecutionEnvironment.getEnvironment().releaseWakeLock();
	}

	private void writeDownloadInfoFile(int count, long lastModified) throws IOException{
		FileOutputStream entryCountOut = new FileOutputStream(WORKDIR +filterhostfile+".DLD_CNT");
		entryCountOut.write((count + "\n").getBytes());
		entryCountOut.write((lastModified + "\n").getBytes());
		entryCountOut.flush();
		entryCountOut.close();
	}

	private boolean updateFilter() throws IOException {
		synchronized (INSTANCE) {
			try {
				updatingFilter = true;
				ExecutionEnvironment.getEnvironment().wakeLock(); //ensure device stays awake until filter update is completed

				OutputStream out = new FileOutputStream(WORKDIR + filterhostfile + ".tmp");
				out.write((DOWNLOADED_FF_PREFIX + new Date() + "from URLs: " + filterReloadURL + "\n").getBytes());

				StringTokenizer urlTokens = new StringTokenizer(filterReloadURL, ";");

				int urlCnt = urlTokens.countTokens();
				int count = 0;
				for (int i = 0; i < urlCnt; i++) {
					String urlStr = urlTokens.nextToken().trim();
					int skippedWildcard = 0;
					try {
						if (!urlStr.equals("")) {
							Logger.getLogger().message("Connecting: " + urlStr);

							InputStream in;
							if (!urlStr.startsWith("file://")) {
								URL url = new URL(urlStr);
								URLConnection con;
								con = url.openConnection();
								con.setRequestProperty("User-Agent", "Mozilla/5.0 (" + System.getProperty("os.name") + "; " + System.getProperty("os.version") + ")");

								con.setConnectTimeout(120000);
								con.setReadTimeout(120000);

								in = new BufferedInputStream(con.getInputStream(), 2048);
							} else
								in = new BufferedInputStream(new FileInputStream(urlStr.substring(7)),2048);
							byte[] buf = new byte[2048];
							int[] r;

							int received = 0;
							int delta = 100000;
							while ((r = readHostFileEntry(in, buf))[1] != -1 && !aborted) {

								if (r[1] != 0) {
									String hostEntry = new String(buf, 0, r[1]);

									if (hostEntry != null && !hostEntry.equals("localhost")) {
										String host = hostEntry;
										if (r[0] == 1) //wildcard
											skippedWildcard++;
										else {
											out.write((host + "\n").getBytes());
											count++;
										}
									}
									received = received + r[1];
									if (received > delta) {
										Logger.getLogger().message("Loading Filter - Bytes received:" + received);
										delta = delta + 100000;
									}
								}
							}
							in.close();
							if (aborted) {
								Logger.getLogger().logLine("Aborting Filter Update!");
								Logger.getLogger().message("Filter Update aborted!");
								out.flush();
								out.close();
								return false;
							}
							if (skippedWildcard != 0)
								Logger.getLogger().logLine("WARNING! - " + skippedWildcard + " skipped entrie(s) for " + urlStr + "! Wildcards are only supported in additionalHosts.txt!");
						}
					} catch (IOException eio) {
						String msg = "ERROR loading filter: " + urlStr;
						Logger.getLogger().message(msg);
						Logger.getLogger().logLine(msg);
						out.close();
						throw eio;
					}

				}
				// update last loaded URL after successfull update
				// setting additionalhosts TS = 0 is a hack here - but it enforces that index is recreated
				updateIndexReloadInfoConfFile(filterReloadURL, "0");
				reloadUrlChanged = false;
				Logger.getLogger().logLine("Updating filter completed!");

				out.flush();
				out.close();

				File ffile = new File(WORKDIR + filterhostfile);

				if (!ffile.exists() || ffile.delete()) {

					new File(WORKDIR + filterhostfile + ".tmp").renameTo(new File(WORKDIR + filterhostfile));
					writeDownloadInfoFile(count, new File(WORKDIR + filterhostfile).lastModified());
				} else
					throw new IOException("Renaming downloaded .tmp file to Filter file failed!");

			} finally {
				ExecutionEnvironment.getEnvironment().releaseWakeLock();
				updatingFilter = false;
				INSTANCE.notifyAll();
			}
		}
		return true; //completed
	}

	public int[] readHostFileEntry(InputStream in, byte[] buf) throws IOException {

		int token = 0;
		int wildcard = 0;

		int r = in.read();
		while (r == 35) {
			//lines starts with # - ignore line!
			r = Utils.skipLine(in);

			if (r != -1)
				r = Utils.skipWhitespace(in, r);
		}

		if (r == -1)
			return new int[]{wildcard, -1};

		if (buf.length == 0)
			throw new IOException("Buffer Overflow!");

		if (r == 42) //wildcard
			wildcard =1;

		buf[0] = (byte)r;
		int pos = 1;

		while (r != -1 && r!=10) {

			while (r != -1 && r!=10) {

				r = in.read();

				if (r == 9 || r == 32 || r == 13) {
					if (token == 1) {
						r = Utils.skipLine(in);
						return new int[]{wildcard, pos};
					} else {
						token = 1;
						wildcard = 0;
						r = Utils.skipWhitespace(in, r);
						pos = 0;
					}
				}

				if (r == 42) //wildcard
					wildcard =1;

				if (r != -1) {
					if (pos == buf.length)
						throw new IOException("Buffer Overflow!");

					if ( r < 32 && r < 9 && r > 13)
						throw new IOException ("Non Printable character: "+r+"("+((char)r)+")");

					buf[pos] = (byte) (r);
					pos++;
				}
			}
		}
		return new int[]{wildcard, pos-1}; //skip linefeed
	}


	private String[] parseHosts(String line) throws IOException {
		if (line.startsWith("#") || line.startsWith("!") || line.trim().equals("") )
			return null;
		String[] result;
		StringTokenizer tokens = new StringTokenizer(line);
		if (tokens.countTokens() >= 2) {
			String ip = tokens.nextToken().trim();
			String host = tokens.nextToken().trim();
			result =  new String[]{ip, host};
		} else { //list with plain hosts
			String ip = "127.0.0.1";
			String host = tokens.nextToken().trim();
			result =  new String[]{ip, host};
		}
		checkHostName(result[1]);
		return result;
	}

	private void checkHostName(String host) throws IOException {
		if (host.length() > 253)
			throw new IOException ("Invalid Hostname: "+host);
	}


	private static boolean updatingFilter = false;

	private void abortFilterUpdate() {
		aborted = true;
		synchronized (INSTANCE) {

			while (updatingFilter) {
				try {
					INSTANCE.wait();
				} catch (InterruptedException e) {
					Logger.getLogger().logException(e);
				}
			}
			aborted = false;
		}
	}

	private void rebuildIndex() throws IOException {
		synchronized (INSTANCE) {
			try {
				updatingFilter = true;

				Logger.getLogger().logLine("Reading filter file and building index...!");
				File filterfile = new File(WORKDIR + filterhostfile);
				File additionalHosts = new File(WORKDIR + "additionalHosts.txt");
				File indexFile = new File(WORKDIR + filterhostfile + ".idx");
				BufferedReader fin = new BufferedReader(new InputStreamReader(new FileInputStream(filterfile)));
				BufferedReader addHostIn = new BufferedReader(new InputStreamReader(new FileInputStream(additionalHosts)));


				int size = 0;

				int ffileCount = -1;
				String firstffLine = fin.readLine();
				boolean ffDownloaded = false;
				if (firstffLine.startsWith(DOWNLOADED_FF_PREFIX)) {
					// downloaded file - we should know the number of entries and the format is plain hosts
					ffDownloaded = true;
					// try to read the info about number of downloaded entries

					try {
						File downloadInfoFile = new File(WORKDIR + filterhostfile + ".DLD_CNT");
						if (downloadInfoFile.exists()) {
							InputStream in = new BufferedInputStream(new FileInputStream(downloadInfoFile));
							byte[] info = new byte[1024];
							int r = Utils.readLineBytesFromStream(in, info, true, true);
							ffileCount = Integer.parseInt(new String(info, 0, r).trim());
							// check if valid
							r = Utils.readLineBytesFromStream(in, info, true, true);
							if (r==-1 || Long.parseLong(new String(info,0,r).trim()) != filterfile.lastModified())
								ffileCount=-1; //invalid

							in.close();
						}
					} catch (Exception e) {
						Logger.getLogger().logLine("Error retrieving Number of downloaded hosts\n"+e.getMessage());
						ffileCount=-1;
					}
				}

				int estimatedIdxCount = ffileCount;
				if (estimatedIdxCount == -1)
					//estimate based on file size
					estimatedIdxCount = Math.max(1, (int) ((filterfile.length() + additionalHosts.length()) / 30));
				else //known ff entry count plus the estimated entries from add hosts.
					estimatedIdxCount= estimatedIdxCount + ((int) (additionalHosts.length() / 20));


				BlockedHosts hostFilterSet = new BlockedHosts(estimatedIdxCount, okCacheSize, filterListCacheSize, hostsFilterOverRule);

				String entry = firstffLine; // first line from filterfile as read above

				boolean skipFFprep = false;

				if (ffDownloaded && ffileCount != -1) {
					// Filterfile known ... We can skip preparation and directly go to additional hosts
					entry = null;
					size = ffileCount;
					skipFFprep = true;
				}

				while (!aborted && (entry != null || fin != addHostIn)) {
					if (entry == null) {
						//ready with filter file continue with additionalHosts
						fin.close();
						fin = addHostIn;
					} else {
						String[] hostEntry = parseHosts(entry);
						if (hostEntry != null && !hostEntry[1].equals("localhost")) {
							hostFilterSet.prepareInsert(hostEntry[1]);
							size++;
						}
					}
					entry = fin.readLine();
				}

				fin.close();
				if (fin != addHostIn)
					addHostIn.close();
				if (aborted) {
					Logger.getLogger().logLine("Aborting Indexing!");
					Logger.getLogger().message("Indexing Aborted!");
					return;
				}

				if (!skipFFprep)
					hostFilterSet.finalPrepare();
				else
					hostFilterSet.finalPrepare(estimatedIdxCount);

				Logger.getLogger().logLine("Building index for " + size + " entries...!");

				fin = new BufferedReader(new InputStreamReader(new FileInputStream(filterfile)));
				addHostIn = new BufferedReader(new InputStreamReader(new FileInputStream(additionalHosts)));
				File uniqueEntriyFile = new File(WORKDIR + "uniqueentries.tmp");
				BufferedOutputStream fout = null;

				if (filterHostsFileRemoveDuplicates) {
					fout = new BufferedOutputStream(new FileOutputStream(uniqueEntriyFile));
					fout.write((firstffLine+"\n").getBytes()); // take over header info from original
				}

				int processed = 0;
				int uniqueEntries = 0;

				if (ffDownloaded)
					fin.readLine(); // skip first comment line

				while (!aborted && ((entry = fin.readLine()) != null || fin != addHostIn)) {
					if (entry == null) {
						//ready with filter file continue with additionalHosts
						fin.close();
						fin = addHostIn;
						ffileCount=uniqueEntries;
					} else {
						String[] hostEntry;
						if (!ffDownloaded || fin == addHostIn )
							hostEntry = parseHosts(entry);
						else // reading downloaded filterfile with known plain hosts format
							hostEntry = new String[] {"",entry};

						if (hostEntry != null && !hostEntry[1].equals("localhost")) {
							if (!hostFilterSet.add(hostEntry[1]))
								;//Logger.getLogger().logLine("Duplicate detected ==>" + entry);
							else {
								uniqueEntries++;
								if (fin != addHostIn && filterHostsFileRemoveDuplicates)
									fout.write((hostEntry[1] + "\n").getBytes()); // create filterhosts without duplicates
							}
							processed++;
							if (processed % 10000 == 0) {
								Logger.getLogger().message("Building index for " + processed + "/" + size + " entries completed!");
							}
						}
					}
				}
				Logger.getLogger().message("Building index for " + processed + "/" + size + " entries completed!");
				fin.close();
				if (fin != addHostIn)
					addHostIn.close();

				if (aborted) {
					Logger.getLogger().logLine("Indexing Aborted!");
					if (filterHostsFileRemoveDuplicates)
						fout.close();
					return;
				}

				if (filterHostsFileRemoveDuplicates) {
					fout.flush();
					fout.close();
					//store unique entries as FilterHosts
					filterfile.delete();
					uniqueEntriyFile.renameTo(filterfile);
					if (skipFFprep){
						//filterFile was changed (unique entries) =>Update Download Info File
						writeDownloadInfoFile(ffileCount, new File(WORKDIR + filterhostfile).lastModified());
					}
				}

				boolean lock = hostFilter != null;
				try {
					if (lock)
						hostFilter.lock(1); // Exclusive Lock ==> No reader allowed during update of hostfilter

					Logger.getLogger().logLine("Persisting index for " + size + " entries...!");
					Logger.getLogger().logLine("Index contains " + uniqueEntries + " unique entries!");

					hostFilterSet.persist(WORKDIR + filterhostfile + ".idx");
					hostFilterSet.clear(); //release memory

					hostFilterSet = BlockedHosts.loadPersistedIndex(indexFile.getAbsolutePath(), false, okCacheSize, filterListCacheSize, hostsFilterOverRule); //loads only file handles not the whole structure.

					if (hostFilter != null) {
						hostFilter.migrateTo(hostFilterSet);

					} else {
						hostFilter = hostFilterSet;
						DNSResponsePatcher.init(hostFilter, TRAFFIC_LOG); //give newly created filter to DNSResponsePatcher
					}
				} finally {
					if (lock)
						hostFilter.unLock(1); //Update done! Release exclusive lock so readers are welcome!
				}
				validIndex = true;
				Logger.getLogger().logLine("Processing new filter file completed!");
				additionalHostsImportTS = "" + additionalHosts.lastModified();
				updateIndexReloadInfoConfFile(filterReloadURL, additionalHostsImportTS); //update last loaded URL and additionalHosts
			} finally {
				updatingFilter = false;
				INSTANCE.notifyAll();
			}
		}
	}

	private void reloadFilter(boolean async) throws IOException {
		try {
			ExecutionEnvironment.getEnvironment().wakeLock(); //ensure device stays awake until filter reload is completed

			File filterfile = new File(WORKDIR + filterhostfile);
			File downloadInfoFile = new File(WORKDIR + filterhostfile + ".DLD_CNT");
			File additionalHosts = new File(WORKDIR + "additionalHosts.txt");
			if (!additionalHosts.exists())
				additionalHosts.createNewFile();

			boolean needRedloadAdditionalHosts = !("" + additionalHosts.lastModified()).equals(additionalHostsImportTS);

			if (filterfile.exists() && downloadInfoFile.exists() && !reloadUrlChanged) {
				nextReload = filterReloadIntervalDays * 24 * 60 * 60 * 1000 + downloadInfoFile.lastModified();
			} else
				nextReload = 0; // reload asap

			File indexFile = new File(WORKDIR + filterhostfile + ".idx");
			if (indexFile.exists() && validIndex && BlockedHosts.checkIndexVersion(indexFile.getAbsolutePath())) {
				hostFilter = BlockedHosts.loadPersistedIndex(indexFile.getAbsolutePath(), false, okCacheSize, filterListCacheSize, hostsFilterOverRule);
				if (needRedloadAdditionalHosts && filterfile.exists() && nextReload != 0) {
					// additionalHosts where modified - reload async and keep current index until reload completed in order to prevent start without any filter!
					new Thread(new AsyncIndexBuilder()).start();
				}
			} else if (filterfile.exists() && nextReload != 0) {
				if (!async) {
					rebuildIndex();
				}
				else
					new Thread(new AsyncIndexBuilder()).start();
			}
		} finally {
			ExecutionEnvironment.getEnvironment().releaseWakeLock();
		}
	}

	private void updateIndexReloadInfoConfFile(String url, String additionalHosts_lastImportTS) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(WORKDIR + "dnsfilter.conf")));
			String ln;
			boolean found1 = false;
			boolean found2 = false;
			while ((ln = reader.readLine()) != null) {
				if (url != null && ln.startsWith("previousAutoUpdateURL")) {
					found1 = true;
					ln = "previousAutoUpdateURL = " + url;
				} else if (additionalHosts_lastImportTS != null && ln.startsWith("additionalHosts_lastImportTS")) {
					found2 = true;
					ln = "additionalHosts_lastImportTS = " + additionalHosts_lastImportTS;
				}
				out.write((ln + "\r\n").getBytes());
			}
			if (!found1 && url != null)
				out.write(("previousAutoUpdateURL = " + url + "\r\n").getBytes());
			if (!found2 && additionalHosts_lastImportTS != null)
				out.write(("additionalHosts_lastImportTS = " + additionalHosts_lastImportTS + "\r\n").getBytes());

			out.flush();
			reader.close();
			OutputStream fout = new FileOutputStream(WORKDIR + "dnsfilter.conf");
			fout.write(out.toByteArray());
			fout.flush();
			fout.close();
		} catch (IOException e) {
			Logger.getLogger().logException(e);
		}
	}

	public void updateFilter(String entries, boolean filter) throws IOException {

		try {

			boolean indexingAborted = false;

			if (updatingFilter) {
				abortFilterUpdate();
				indexingAborted = true;
			}


			synchronized (INSTANCE) {

				String copyPasteStartSection = "##### AUTOMATIC ENTRIES BELOW! #####";
				String whitelistSection = "## Whitelisted Entries! ##";
				String blacklistSection = "## Blacklisted Entries! ##";

				if (entries.trim().equals("") || hostFilter == null)
					return;

				StringTokenizer entryTokens = new StringTokenizer(entries, "\n");
				HashSet<String> entriestoChange = new HashSet<String>();

				// find which entries need to be overwritten
				while (entryTokens.hasMoreTokens()) {
					String entry = entryTokens.nextToken().trim();
					boolean filterContains = hostFilter.contains(entry);
					if ((filter && !filterContains) || (!filter && filterContains))
						entriestoChange.add(entry);
				}

				// update additional hosts file
				File additionalHosts = new File(WORKDIR + "additionalHosts.txt");
				File additionalHostsNew = new File(WORKDIR + "additionalHosts.txt.tmp");

				BufferedReader addHostIn = new BufferedReader(new InputStreamReader(new FileInputStream(additionalHosts)));
				BufferedWriter addHostOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(additionalHostsNew)));

				String entry = null;

				boolean copyPasteSection = false;
				boolean listSection = false;
				while ((entry = addHostIn.readLine()) != null) {
					String host = entry;
					boolean hostEntry = !(entry.trim().equals("") && !entry.startsWith("#"));
					if (entry.startsWith("!"))
						host = entry.trim().substring(1);
					if (!hostEntry || !entriestoChange.contains(host)) {// take over entries with no change required
						addHostOut.write(entry + "\n");
					}
					if (!copyPasteSection)
						copyPasteSection = entry.startsWith(copyPasteStartSection);
					if (!listSection) {
						listSection = filter && entry.startsWith(blacklistSection) || !filter && entry.startsWith(whitelistSection);
						if (listSection) //write entries to be changed in list section within additional hosts
							writeNewEntries(filter, entriestoChange, addHostOut);
					}
				}

				addHostIn.close();

				//write copy paste section comment into add Hosts file if not there
				if (!copyPasteSection)
					addHostOut.write("\n" + copyPasteStartSection + "\n");

				if (!listSection) {
					if (filter)
						addHostOut.write("\n" + blacklistSection + "\n");
					else
						addHostOut.write("\n" + whitelistSection + "\n");

					writeNewEntries(filter, entriestoChange, addHostOut);
				}

				addHostOut.flush();
				addHostOut.close();

				additionalHosts.delete();

				additionalHostsNew.renameTo(additionalHosts);

				//index was updated ==> update timestamp in order to avoid reindexing
				additionalHostsImportTS = "" + additionalHosts.lastModified();
				updateIndexReloadInfoConfFile(filterReloadURL, additionalHostsImportTS); //update last loaded URL and additionalHosts

				Logger.getLogger().message("Updated " + entriestoChange.size() + " host(s)!");

				if (indexingAborted) {
					//retrigger aborted updatingFilter
					new Thread(new AsyncIndexBuilder()).start();
				}

			}
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	@Override
	public String getVersion() {
		return VERSION;
	}

	@Override
	public int openConnectionsCount() {
		return  DNSResolver.getResolverCount();
	}

	@Override
	public String getLastDNSAddress() {
		return DNSCommunicator.getInstance().getLastDNSAddress();
	}

	@Override
	public void restart() throws IOException {
		try {
			stop();
			init();
			ExecutionEnvironment.getEnvironment().onReload();
		} catch (IOException e) {
			throw new ConfigurationAccessException(e.getMessage(), e);
		}
	}

	@Override
	public long[] getFilterStatistics() {
		return new long[] { DNSResponsePatcher.getOkCount(),  DNSResponsePatcher.getFilterCount()};
	}

	private void writeNewEntries(boolean filter, HashSet<String> entriestoChange, BufferedWriter addHostOut) throws IOException {

		String excludePref="";
		if (!filter)
			excludePref="!";

		if (hostsFilterOverRule == null) {
			hostsFilterOverRule = new Hashtable();
			hostFilter.setHostsFilterOverRule(hostsFilterOverRule);
		}

		Iterator<String> entryit = entriestoChange.iterator();
		while (entryit.hasNext()) {
			String entry = entryit.next();
			boolean skip = false;

			hostsFilterOverRule.remove(entry);
			hostFilter.clearCache(entry);

			// skip previously whitelisted entry which shall be blacklisted again and is already part of the default filters
			// Requires that the index is up-to-date (lastFinishedIndexRunTS > lastRequestedIndexRunTS)
			skip = (filter && hostFilter.contains(entry));

			if (!skip) {
				addHostOut.write( "\n"+excludePref + entry);
				if (!filter) {
					hostsFilterOverRule.put(entry, false);  //whitelisting via hostfilter overrule
				}
				else
					hostFilter.update(entry); //update index directly
			}
			if (filter)
				hostFilter.updatePersist();
		}
	}

	private void initEnv() {

		debug = false;
		filterReloadURL = null;
		filterhostfile = null;
		filterReloadIntervalDays = 4;
		nextReload = 0;
		reloadUrlChanged = false;
		filterHostsFileRemoveDuplicates = false;
		validIndex = true;
		hostFilter = null;
		hostsFilterOverRule = null;
		additionalHostsImportTS = "0";
		reloading_filter = false;

	}

	private String getFilterReloadURL(Properties config) {
		String urls = config.getProperty("filterAutoUpdateURL", "");
		String url_switchs = config.getProperty("filterAutoUpdateURL_switchs", "");

		StringTokenizer urlTokens = new StringTokenizer(urls, ";");
		StringTokenizer urlSwitchTokens = new StringTokenizer(url_switchs, ";");

		int count = urlTokens.countTokens();

		String result = "";
		String seperator = "";

		for (int i = 0; i < count; i++) {
			String urlStr = urlTokens.nextToken().trim();
			boolean active = true;
			if (urlSwitchTokens.hasMoreTokens())
				active = Boolean.parseBoolean(urlSwitchTokens.nextToken().trim());

			if (active) {
				result = result + seperator + urlStr;
				seperator = "; ";
			}
		}
		return result;
	}


	public void init() throws IOException {

		try {
			if (!serverStopped)
				throw new IllegalStateException("Cannot start! Already running!");

			initEnv();

			Logger.getLogger().logLine("***Initializing PersonalDNSFilter Version " + VERSION + "!***");
			Logger.getLogger().logLine("Using Directory: "+WORKDIR);

			byte[] configBytes = readConfig();
			config = new Properties();
			config.load(new ByteArrayInputStream(configBytes));

			serverStopped = false;

			//start remote Control server if configured and not started already
			if (remoteAccessManager == null) {
				try {

					int port = Integer.parseInt(config.getProperty("server_remote_ctrl_port", "-1"));
					String keyphrase = config.getProperty("server_remote_ctrl_keyphrase", "");
					if (port != -1)
						remoteAccessManager = new RemoteAccessServer(port, keyphrase);
				} catch (Exception e) {
					Logger.getLogger().logException(e);
				}
			}

			try {
				okCacheSize = Integer.parseInt(config.getProperty("allowedHostsCacheSize", "1000").trim());
				filterListCacheSize = Integer.parseInt(config.getProperty("filterHostsCacheSize", "1000").trim());
			} catch (NumberFormatException nfe) {
				Logger.getLogger().logLine("Cannot parse cache size configuration!");
				throw new IOException(nfe);
			}

			//wake lock if configured
			if (config.getProperty("androidKeepAwake", "true").equalsIgnoreCase("true"))
				ExecutionEnvironment.getEnvironment().wakeLock();

			additionalHostsImportTS = config.getProperty("additionalHosts_lastImportTS", "0");

			//Init traffic Logger
			try {

				if (config.getProperty("enableTrafficLog", "true").equalsIgnoreCase("true")) {
					TRAFFIC_LOG = new FileLogger(WORKDIR + "log",
							config.getProperty("trafficLogName", "trafficlog"),
							Integer.parseInt(config.getProperty("trafficLogSize", "1048576").trim()),
							Integer.parseInt(config.getProperty("trafficLogSlotCount", "2").trim()),
							"timestamp, client:port, request type, domain name, answer");

					((FileLogger) TRAFFIC_LOG).enableTimestamp(true);

					Logger.setLogger(TRAFFIC_LOG, "TrafficLogger");
				} else TRAFFIC_LOG = null;

			} catch (NumberFormatException nfe) {
				Logger.getLogger().logLine("Cannot parse log configuration!");
				throw new IOException(nfe);
			}

			debug = Boolean.parseBoolean(config.getProperty("debug", "false"));
			//filterHostsFileRemoveDuplicates = Boolean.parseBoolean(config.getProperty("filterHostsFileRemoveDuplicates", "false"));
			filterHostsFileRemoveDuplicates = true;

			filterhostfile = config.getProperty("filterHostsFile");

			if (filterhostfile != null) {

				// load filter overrule values

				Iterator entries = config.entrySet().iterator();

				while (entries.hasNext()) {
					Entry entry = (Entry) entries.next();
					String key = (String) entry.getKey();
					if (key.startsWith("filter.")) {
						if (hostsFilterOverRule == null)
							hostsFilterOverRule = new Hashtable();
						hostsFilterOverRule.put(key.substring(7), new Boolean(Boolean.parseBoolean(((String) entry.getValue()).trim())));
					}
				}

				//load whitelisted hosts from additionalHosts.txt

				File additionalHosts = new File(WORKDIR + "additionalHosts.txt");
				if (additionalHosts.exists()) {
					BufferedReader addHostIn = new BufferedReader(new InputStreamReader(new FileInputStream(additionalHosts)));
					String entry = null;
					while ((entry = addHostIn.readLine()) != null) {
						if (entry.startsWith("!")) {
							if (hostsFilterOverRule == null)
								hostsFilterOverRule = new Hashtable();
							hostsFilterOverRule.put(entry.substring(1).trim(), new Boolean(false));
						}
					}
					addHostIn.close();
				}

				// trigger regular filter update when configured
				filterReloadURL = getFilterReloadURL(config);
				filterReloadIntervalDays = Integer.parseInt(config.getProperty("reloadIntervalDays", "4"));
				String previousReloadURL = config.getProperty("previousAutoUpdateURL");

				if (filterReloadURL != null)
					reloadUrlChanged = !filterReloadURL.equals(previousReloadURL);

				// Load filter file
				reloadFilter(true);

				if (filterReloadURL != null) {

					autoFilterUpdater = new AutoFilterUpdater();
					Thread t = new Thread(autoFilterUpdater);
					t.setDaemon(true);
					t.start();
				}

				DNSResponsePatcher.init(hostFilter, TRAFFIC_LOG);
			}

		} catch (IOException e) {
			throw e;
		}
	}


	@Override
	public void stop() throws IOException {

		if (serverStopped)
			return;

		abortFilterUpdate();

		synchronized (this) {
			if (autoFilterUpdater != null) {
				autoFilterUpdater.stop();
				autoFilterUpdater = null;
			}

			this.notifyAll();
			if (hostFilter != null)
				hostFilter.clear();

			DNSResponsePatcher.init(null, null);

			if (TRAFFIC_LOG != null) {
				TRAFFIC_LOG.closeLogger();
				Logger.removeLogger("TrafficLogger");
			}

			serverStopped = true;
			ExecutionEnvironment.getEnvironment().releaseAllWakeLocks();
		}
	}



	public boolean canStop() {

		return !reloading_filter;
		//return true;
	}

}
