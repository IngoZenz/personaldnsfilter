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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;

import util.ExecutionEnvironment;
import util.FileLogger;
import util.Logger;
import util.LoggerInterface;


public class DNSFilterManager implements LoggerInterface
{
	public static final String VERSION = "1.50.15";
	static public boolean debug;
	static public String WORKDIR = "";	
	private static String filterReloadURL;
	private static String filterhostfile;
	private static long filterReloadIntervalDays;
	private static long nextReload;
	private static int okCacheSize = 500;
	private static int filterListCacheSize = 500;
	private static boolean reloadUrlChanged;
	private static String additionalHostsImportTS = "0";
	private static boolean validIndex;
	
	private static LoggerInterface TRAFFIC_LOG;

	private static BlockedHosts hostFilter = null;
	private static Hashtable hostsFilterOverRule = null;
	private boolean serverStopped = false;
	private boolean reloading_filter = false;

	
	protected Properties config =null;
	
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
						reloading_filter=true;
						Logger.getLogger().logLine("DNS Filter: Reloading hosts filter ...");						
						updateFilter();
						validIndex = false;
						reloadFilter(false);
						Logger.getLogger().logLine("Reloading hosts filter ... completed!");
						waitTime = filterReloadIntervalDays * 24 * 60 * 60 * 1000;
						nextReload = System.currentTimeMillis() + waitTime;						
						retry = 0;
					} catch (Exception e) {
						Logger.getLogger().logLine("Cannot update hosts filter file!");
						Logger.getLogger().logLine(e.getMessage());	
						if (retry < 10) {
							if (retry < 5)
								waitTime = 60000;
							else 
								waitTime = 3600000; // retry after 1 h
							
							nextReload = System.currentTimeMillis() + waitTime;							
							Logger.getLogger().logLine("Retry at: "+new Date(nextReload));
							retry++;							
						} else {
							Logger.getLogger().logLine("Giving up! Reload skipped!");
							waitTime = filterReloadIntervalDays * 24 * 60 * 60 * 1000;
							nextReload = System.currentTimeMillis() + waitTime;				
							retry = 0;
						}
					} finally {
						reloading_filter=false;
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

	private void updateFilter() throws IOException {
		try {	
			ExecutionEnvironment.getEnvironment().wakeLock(); //ensure device stays awake until filter update is completed
			
			OutputStream out = new FileOutputStream(WORKDIR + filterhostfile+".tmp");
			out.write((("# Hosts Filter File from " + filterReloadURL + "\n").getBytes()));
			out.write(("# Last Update:" + new Date() + "\n").getBytes());
			
			StringTokenizer urlTokens = new StringTokenizer(filterReloadURL,";");			
	
			int urlCnt = urlTokens.countTokens();
			for (int i = 0; i < urlCnt; i++) {
				String urlStr = urlTokens.nextToken().trim();
				if (!urlStr.equals("")) {
					Logger.getLogger().logLine("DNS Filter: Updating filter from " + urlStr + "...");
					out.write(("\n# Load Filter from URL:"+urlStr+"\n").getBytes());
					URL url = new URL(urlStr);
					URLConnection con;					
					con = url.openConnection();
			
					con.setConnectTimeout(120000);
					con.setReadTimeout(120000);
					
					InputStream in = con.getInputStream();			
					byte[] buf = new byte[10000];
					int r;	
	
					int received = 0;
					int delta = 100000;
					while ((r = in.read(buf)) != -1) {
						out.write(buf, 0, r);
						received = received + r;
						if (received > delta) {
							Logger.getLogger().logLine("Bytes received:" + received);
							delta = delta + 100000;
						}
					}
				}
			}
			Logger.getLogger().logLine("Updating filter completed!");
	
			out.flush();
			out.close();
			new File(WORKDIR + filterhostfile).delete();
			new File(WORKDIR + filterhostfile+".tmp").renameTo(new File(WORKDIR + filterhostfile));

		} finally {
			ExecutionEnvironment.getEnvironment().releaseWakeLock();			
		}
	}
	
	private String[] parseHosts(String line) {
		if (line.startsWith("#")|| line.equals(""))
			return null;
		StringTokenizer tokens = new StringTokenizer(line);
		if (tokens.countTokens() >=2) {
			String ip = tokens.nextToken().trim();
			String host = tokens.nextToken().trim();
			return new String[]{ip,host};
		} else { //list with plain hosts
			String ip = "127.0.0.1";
			String host = tokens.nextToken().trim();
			return new String[]{ip,host};
		}
	}
	
	private void rebuildIndex() throws IOException {
		Logger.getLogger().logLine("Reading filter file and building index...!");
		File filterfile = new File(WORKDIR + filterhostfile);
		File additionalHosts = new File(WORKDIR + "additionalHosts.txt");
		File indexFile = new File(WORKDIR + filterhostfile+".idx");
		BufferedReader fin = new BufferedReader(new InputStreamReader(new FileInputStream(filterfile)));
		BufferedReader addHostIn = new BufferedReader(new InputStreamReader(new FileInputStream(additionalHosts)));
		String entry = null;
		int size = 0;

		BlockedHosts hostFilterSet = new BlockedHosts(Math.max(1, (int) ( (filterfile.length() + additionalHosts.length()) / 30)), okCacheSize, filterListCacheSize, hostsFilterOverRule);
		
		while ((entry = fin.readLine()) != null || fin != addHostIn) {
			if (entry == null) {
				//ready with filter file continue with additionalHosts
				fin.close();
				fin = addHostIn;
			} else {
				String[] hostEntry = parseHosts(entry);
				if (hostEntry != null) {
					hostFilterSet.prepareInsert(hostEntry[1]);
					size++;
				}
			}
		}		

		fin.close();

		hostFilterSet.finalPrepare();

		Logger.getLogger().logLine("Building index for " + size + " entries...!");

		fin = new BufferedReader(new InputStreamReader(new FileInputStream(filterfile)));
		addHostIn = new BufferedReader(new InputStreamReader(new FileInputStream(additionalHosts)));

		int processed = 0;
		int uniqueEntries = 0;
		while ((entry = fin.readLine()) != null || fin != addHostIn) {
			if (entry == null) {
				//ready with filter file continue with additionalHosts
				fin.close();
				fin = addHostIn;
			} else {
				String[] hostEntry = parseHosts(entry);
				if (hostEntry != null && !hostEntry[1].equals("localhost")) {
					if (!hostFilterSet.add(hostEntry[1]))
						;//Logger.getLogger().logLine("Duplicate detected ==>" + entry);
					else uniqueEntries++;

					processed++;
					if (processed % 10000 == 0) {
						Logger.getLogger().logLine("Building index for " + processed + "/" + size + " entries completed!");
					}
				}
			}
		}
		fin.close();

		try {
			if (hostFilter != null)
				hostFilter.lock(1); // Exclusive Lock ==> No reader allowed during update of hostfilter
			
			Logger.getLogger().logLine("Persisting index for " + size + " entries...!");
			Logger.getLogger().logLine("Index contains "+uniqueEntries+" unique entries!");
			
			hostFilterSet.persist(WORKDIR+filterhostfile+".idx");
			hostFilterSet.clear(); //release memory
			
			hostFilterSet = BlockedHosts.loadPersistedIndex(indexFile.getAbsolutePath(), false, okCacheSize, filterListCacheSize, hostsFilterOverRule); //loads only file handles not the whole structure.  
			
			if (hostFilter != null)	{		
				hostFilter.migrateTo(hostFilterSet);
				
			} else {
				hostFilter = hostFilterSet;
				DNSResponsePatcher.init(hostFilter, TRAFFIC_LOG); //give newly created filter to DNSResponsePatcher
			}
		} finally {
			hostFilter.unLock(1); //Update done! Release exclusive lock so readers are welcome!
		}
		validIndex = true;
		Logger.getLogger().logLine("Processing new filter file completed!");
		additionalHostsImportTS = ""+additionalHosts.lastModified();
		updateIndexReloadInfoConfFile(filterReloadURL, additionalHostsImportTS); //update last loaded URL and additionalHosts
	}

	private void reloadFilter(boolean async) throws IOException {
		try {
			ExecutionEnvironment.getEnvironment().wakeLock(); //ensure device stays awake until filter reload is completed		

			File filterfile = new File(WORKDIR + filterhostfile);
			File additionalHosts = new File(WORKDIR + "additionalHosts.txt");
			if (!additionalHosts.exists())
				additionalHosts.createNewFile();

			boolean needRedloadAdditionalHosts = !(""+additionalHosts.lastModified()).equals(additionalHostsImportTS);
	
			if (filterfile.exists() && !reloadUrlChanged) {
				nextReload = filterReloadIntervalDays * 24 * 60 * 60 * 1000 + filterfile.lastModified();
			} else
				nextReload = 0; // reload asap
	
			File indexFile = new File(WORKDIR + filterhostfile+".idx");
			if (indexFile.exists() && validIndex && BlockedHosts.checkIndexVersion(indexFile.getAbsolutePath()) && !needRedloadAdditionalHosts) {
				hostFilter = BlockedHosts.loadPersistedIndex(indexFile.getAbsolutePath(), false, okCacheSize, filterListCacheSize, hostsFilterOverRule);
			} else if (filterfile.exists()) {
				if (!async)
					rebuildIndex();	
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
			}
			else if (ln.startsWith("additionalHosts_lastImportTS")) {
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
	
	
	private void initStatics() {
		debug = false;		
		filterReloadURL = null;
		filterhostfile = null;
		filterReloadIntervalDays = 4;
		nextReload = 0;
		reloadUrlChanged = false;
		validIndex = true;
		hostFilter = null;
		hostsFilterOverRule = null;
		additionalHostsImportTS = "0";

	}

	public void init() throws Exception {
		initStatics();
		boolean filterEnabled = false;
		try {
			Logger.getLogger().logLine("***Initializing PersonalDNSFilter Version "+VERSION+"!***");
			
			config = new Properties();
			FileInputStream in = new FileInputStream(WORKDIR + "dnsfilter.conf");
			config.load(in);
			in.close();

			additionalHostsImportTS = config.getProperty("additionalHosts_lastImportTS","0");

			//Init traffic Logger			
			try {	
				
				if (config.getProperty("enableTrafficLog", "true").equalsIgnoreCase("true")) {
					TRAFFIC_LOG =  new FileLogger(WORKDIR+"log", 
							config.getProperty("trafficLogName","trafficlog"), 
							Integer.parseInt(config.getProperty("trafficLogSize","1048576").trim()),
							Integer.parseInt(config.getProperty("trafficLogSlotCount","2").trim()),
							"timestamp, client:port, request type, domain name, answer");
					
					((FileLogger)TRAFFIC_LOG).enableTimestamp(true);
					
					Logger.setLogger(TRAFFIC_LOG,"TrafficLogger");
				} 
				else TRAFFIC_LOG= null;
				
			} catch (NumberFormatException nfe) {
				Logger.getLogger().logLine("Cannot parse log configuration!");
				throw nfe;
			}
			
			debug = Boolean.parseBoolean(config.getProperty("debug", "false"));
			
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

				// trigger regular filter update when configured
				filterReloadURL = config.getProperty("filterAutoUpdateURL");
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

		serverStopped = true;
		this.notifyAll();
		if (hostFilter != null)
			hostFilter.clear();
		
		if (TRAFFIC_LOG!=null) {
			TRAFFIC_LOG.closeLogger();
			Logger.removeLogger("TrafficLogger");	
		}
	}
	
	public boolean canStop(){
		return !reloading_filter;
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
	public void closeLogger() {
		// TODO Auto-generated method stub
		
	}
}
