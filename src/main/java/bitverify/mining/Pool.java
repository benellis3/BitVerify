package bitverify.mining;

import java.util.ArrayList;

import bitverify.entries.Entry;

/**
 * This class is responsible for passing entries to the miner, it may soon be obsolete
 * depending on how the database pool is used.
 * @author Alex Day
 */
public class Pool {
	private int maxEntries;
	private ArrayList<Entry> entryPool;
	
	public Pool(){
		entryPool = new ArrayList<Entry>();
	}
	
	public void setMaxEntries(int x){
		maxEntries = x;
	}
	
	//Network module will pass entries to pool
	public void addToPool(Entry e){
		if (entryPool.size() < maxEntries){
			entryPool.add(e);
		}
	}
	
	//Take from pool for mining
	public Entry takeFromPool(){
		if (getNumEntries() > 0){
			Entry e = entryPool.get(0);
			entryPool.remove(0);
			return e;
		}
		return null;
	}
	
	public int getNumEntries(){
		return entryPool.size();
	}
}
