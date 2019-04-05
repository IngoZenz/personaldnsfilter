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
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;

import util.ExecutionEnvironment;

import util.FileLogger;
import util.Logger;
import util.LoggerInterface;
import util.Utils;


public class DNSFilterManager implements LoggerInterface {
	public static final String VERSION = "1.50.32-dev";
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
	private static boolean indexAbort = false;

	private static long lastFinishedIndexRunTS = 0;
	private static long lastRequestedIndexRunTS = 0;

	private static LoggerInterface TRAFFIC_LOG;

	private static BlockedHosts hostFilter = null;
	private static Hashtable hostsFilterOverRule = null;
	private boolean serverStopped = false;
	private boolean reloading_filter = false;


	protected Properties config = null;


	private class AsyncIndexBuilder implements Runnable {

		public AsyncIndexBuilder() {
			lastRequestedIndexRunTS = System.currentTimeMillis();
		}

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

		public AutoFilterUpdater(Object monitor) {
			this.monitor = monitor;
		}

		private void waitUntilNextFilterReload() throws InterruptedException {
			// This strange kind of waiting per 10 seconds interval is needed for Android as during device sleep the timer is stopped.
			// This caused the problem that on Android the filter was never updated during runtime but only when restarting the app.
			synchronized (monitor) {
				while (nextReload > System.currentTimeMillis() && !serverStopped)
					monitor.wait(10000);
			}
		}

		@Override
		public void run() {

			synchronized (monitor) {

				int retry = 0;
				long waitTime;

				while (!serverStopped) {

					Logger.getLogger().logLine("DNS Filter: Next filter reload:" + new Date(nextReload));
					try {
						waitUntilNextFilterReload();
					} catch (InterruptedException e) {
						// nothing to do!
					}
					if (serverStopped)
						break;
					try {
						reloading_filter = true;
						Logger.getLogger().logLine("DNS Filter: Reloading hosts filter ...");
						updateFilter();
						validIndex = false;
						reloadFilter(false);
						Logger.getLogger().logLine("Reloading hosts filter ... completed!");
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
			}
		}
	}

	public DNSFilterManager() {
	}

	public Properties getConfig() {
		return config;
	}

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

	private void writeDownloadInfoFile(int count, long lastModified) throws IOException{
		FileOutputStream entryCountOut = new FileOutputStream(WORKDIR +filterhostfile+".DLD_CNT");
		entryCountOut.write((count + "\n").getBytes());
		entryCountOut.write((lastModified + "\n").getBytes());
		entryCountOut.flush();
		entryCountOut.close();
	}

	private void updateFilter() throws IOException {
		try {
			ExecutionEnvironment.getEnvironment().wakeLock(); //ensure device stays awake until filter update is completed

			OutputStream out = new FileOutputStream(WORKDIR + filterhostfile + ".tmp");
			out.write(("# Downloaded by personalDNSFilter at: " + new Date() + "from URLs: "+  filterReloadURL+"\n").getBytes());

			StringTokenizer urlTokens = new StringTokenizer(filterReloadURL, ";");

			int urlCnt = urlTokens.countTokens();
			int count = 0;
			for (int i = 0; i < urlCnt; i++) {
				String urlStr = urlTokens.nextToken().trim();
				try {
					if (!urlStr.equals("")) {
						Logger.getLogger().message("Connecting: "+urlStr);
						URL url = new URL(urlStr);
						URLConnection con;
						con = url.openConnection();

						con.setConnectTimeout(120000);
						con.setReadTimeout(120000);

						InputStream in = new BufferedInputStream(con.getInputStream(),2048);
						byte[] buf = new byte[2048];
						int r;

						int received = 0;
						int delta = 100000;
						while ((r = Utils.readLineBytesFromStream(in,buf)) != -1) {

							String[] hostEntry = parseHosts(new String(buf,0,r).trim());

							if (hostEntry != null && !hostEntry[1].equals("localhost")) {
								String host = hostEntry[1];
								if (host.indexOf('*') != -1)
									throw new IOException("Wildcards only supported in additionalHosts.txt! - Wrong entry: " + host);
								out.write((host + "\n").getBytes());

								count++;
							}
							received = received + r;
							if (received > delta) {
								Logger.getLogger().message("Bytes received:" + received);
								delta = delta + 100000;
							}
						}
						in.close();
					}
				} catch (IOException eio) {
					String msg = "ERROR loading filter: "+urlStr;
					Logger.getLogger().message(msg);
					Logger.getLogger().logLine(msg);
					out.close();
					throw eio;
				}
			}

			Logger.getLogger().logLine("Updating filter completed!");

			out.flush();
			out.close();

			File ffile = new File(WORKDIR + filterhostfile);

			if (!ffile.exists() || ffile.delete()) {

				new File(WORKDIR + filterhostfile + ".tmp").renameTo(new File(WORKDIR + filterhostfile));
				writeDownloadInfoFile(count, new File(WORKDIR + filterhostfile).lastModified());
			} else throw new IOException("Renaming downloaded .tmp file to Filter file failed!");

		} finally {
			ExecutionEnvironment.getEnvironment().releaseWakeLock();
		}
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


	private static Object INDEXSYNC= new Object();
	private static boolean indexing = false;

	private void abortIndexing() {
		indexAbort = true;
		synchronized (INDEXSYNC) {

			while (indexing) {
				try {
					INDEXSYNC.wait();
				} catch (InterruptedException e) {
					Logger.getLogger().logException(e);
				}
			}
			indexAbort = false;
		}
	}

	private void rebuildIndex() throws IOException {
		synchronized (INDEXSYNC) {
			try {
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
				if (firstffLine.startsWith("# Downloaded by personalDNSFilter")) {
					// downloaded file - we should know the number of entries and the format is plain hosts
					ffDownloaded = true;
					// try to read the info about number of downloaded entries

					try {
						File downloadInfoFile = new File(WORKDIR + filterhostfile + ".DLD_CNT");
						if (downloadInfoFile.exists()) {
							InputStream in = new BufferedInputStream(new FileInputStream(downloadInfoFile));
							byte[] info = new byte[1024];
							int r = Utils.readLineBytesFromStream(in, info);
							ffileCount = Integer.parseInt(new String(info, 0, r));
							// check if valid
							r = Utils.readLineBytesFromStream(in, info);
							if (r==-1 || Long.parseLong(new String(info,0,r)) != filterfile.lastModified())
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

				while (!indexAbort && (entry != null || fin != addHostIn)) {
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
				if (indexAbort) {
					Logger.getLogger().logLine("Indexing Aborted!");
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

				if (filterHostsFileRemoveDuplicates)
					fout = new BufferedOutputStream(new FileOutputStream(uniqueEntriyFile));

				int processed = 0;
				int uniqueEntries = 0;

				if (ffDownloaded)
					fin.readLine(); // skip first comment line

				while (!indexAbort && ((entry = fin.readLine()) != null || fin != addHostIn)) {
					if (entry == null) {
						//ready with filter file continue with additionalHosts
						fin.close();
						fin = addHostIn;
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

				if (indexAbort) {
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

				try {
					if (hostFilter != null)
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
					hostFilter.unLock(1); //Update done! Release exclusive lock so readers are welcome!
				}
				validIndex = true;
				lastFinishedIndexRunTS = System.currentTimeMillis();
				Logger.getLogger().logLine("Processing new filter file completed!");
				additionalHostsImportTS = "" + additionalHosts.lastModified();
				updateIndexReloadInfoConfFile(filterReloadURL, additionalHostsImportTS); //update last loaded URL and additionalHosts
			} finally {
				indexing = false;
				INDEXSYNC.notifyAll();
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
				if (needRedloadAdditionalHosts && filterfile.exists()) {
					// additionalHosts where modified - reload async and keep current index until reload completed in order to prevent start without any filter!
					new Thread(new AsyncIndexBuilder()).start();
				}
			} else if (filterfile.exists()) {
				if (!async) {
					lastRequestedIndexRunTS = System.currentTimeMillis();
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
				if (ln.startsWith("previousAutoUpdateURL")) {
					found1 = true;
					ln = "previousAutoUpdateURL = " + url;
				} else if (ln.startsWith("additionalHosts_lastImportTS")) {
					found2 = true;
					ln = "additionalHosts_lastImportTS = " + additionalHosts_lastImportTS;
				}
				out.write((ln + "\r\n").getBytes());
			}
			if (!found1)
				out.write(("previousAutoUpdateURL = " + url + "\r\n").getBytes());
			if (!found2)
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
			boolean hostEntry =  !(entry.trim().equals("") &&!entry.startsWith("#"));
			if (entry.startsWith("!"))
				host = entry.trim().substring(1);
			if (!hostEntry || !entriestoChange.contains(host)) {// take over entries with no change required
				addHostOut.write(entry+"\n");
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
			addHostOut.write("\n"+copyPasteStartSection+"\n");

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

		if (entriestoChange.size()>0)
			abortIndexing(); //abort eventually running interferring indexing - will be retriggerd below

		additionalHostsNew.renameTo(additionalHosts);

		if (entriestoChange.size()>0) {
			new Thread(new AsyncIndexBuilder()).start(); //trigger reindexing
		}
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

			if (lastFinishedIndexRunTS > lastRequestedIndexRunTS) {
				hostsFilterOverRule.remove(entry);
				// skip previously whitelisted entry which shall be blacklisted again and is already part of the default filters
				// Requires that the index is up-to-date (lastFinishedIndexRunTS > lastRequestedIndexRunTS)
				skip = (filter && hostFilter.contains(entry));
			}
			if (!skip) {
				addHostOut.write( "\n"+excludePref + entry);
				hostsFilterOverRule.put(entry, filter);  //write filter in order to take effect immediately
			}

		}
	}

	private void initStatics() {
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


	public void init() throws Exception {
		initStatics();
		boolean filterEnabled = false;
		try {
			Logger.getLogger().logLine("***Initializing PersonalDNSFilter Version " + VERSION + "!***");

			config = new Properties();
			FileInputStream in = new FileInputStream(WORKDIR + "dnsfilter.conf");
			config.load(in);
			in.close();

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
				throw nfe;
			}

			debug = Boolean.parseBoolean(config.getProperty("debug", "false"));
			filterHostsFileRemoveDuplicates = Boolean.parseBoolean(config.getProperty("filterHostsFileRemoveDuplicates", "false"));

			filterhostfile = config.getProperty("filterHostsFile");

			if (filterhostfile != null) {
				filterEnabled = true;
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

					Thread t = new Thread(new AutoFilterUpdater(this));
					t.setDaemon(true);
					t.start();
				}

				DNSResponsePatcher.init(hostFilter, TRAFFIC_LOG);
			}

		} catch (Exception e) {
			throw e;
		}
	}


	public synchronized void stop() {

		abortIndexing();
		serverStopped = true;
		this.notifyAll();
		if (hostFilter != null)
			hostFilter.clear();

		if (TRAFFIC_LOG != null) {
			TRAFFIC_LOG.closeLogger();
			Logger.removeLogger("TrafficLogger");
		}
	}

	public boolean canStop() {

		return !reloading_filter;
		//return true;
	}


	@Override
	public void logLine(String txt) {
		System.out.println(txt);
	}

	@Override
	public void logException(Exception e) {
		e.printStackTrace(System.out);
	}

	@Override
	public void log(String txt) {
		System.out.print(txt);
	}

	@Override
	public void message(String txt)  {
		System.out.print(txt);
	}

	@Override
	public void closeLogger() {
		// TODO Auto-generated method stub

	}
}
