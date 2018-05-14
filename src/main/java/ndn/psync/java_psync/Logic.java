package ndn.psync.java_psync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.MetaInfo;
import net.named_data.jndn.Name;
import net.named_data.jndn.Name.Component;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.MemoryContentCache;
import net.named_data.jndn.util.MemoryContentCache.PendingInterest;
import se.rosenbaum.iblt.Cell;
import se.rosenbaum.iblt.IBLT;
import se.rosenbaum.iblt.data.IntegerData;
import se.rosenbaum.iblt.hash.HashFunction;
import se.rosenbaum.iblt.hash.HashFunctions;
import se.rosenbaum.iblt.hash.IntegerDataHashFunction;
import se.rosenbaum.iblt.hash.IntegerDataSubtablesHashFunctions;

public class Logic {
	
	public Logic(int expectedNumEntries, Face face, Name syncPrefix, Name userPrefix,
	             double syncReplyFreshness, double helloReplyFreshness, KeyChain keyChain) //UpdateCallback onUpdateCallBack)
	{
		m_face = face;
		m_syncPrefix = syncPrefix;
		m_expectedNumEntries = expectedNumEntries;
		m_syncReplyFreshness = syncReplyFreshness;
		m_helloReplyFreshness = helloReplyFreshness;
		m_keyChain = keyChain;
		
		m_expectedNumEntries = (int) (m_expectedNumEntries*1.5);

		// Make number of cells (buckets) a multiple of hash function count (N_Hash)
		while (true) {
	        if (m_expectedNumEntries % m_hashFunctionCount == 0) {
	        	break;
	        } else {
	        	++m_expectedNumEntries;
	        }
		}
		// m_expectedNumEntries = (int) (m_expectedNumEntries*1.5);
		Cell[] cells = new Cell[m_expectedNumEntries];
        HashFunction<IntegerData, IntegerData> hashFunction = new IntegerDataHashFunction();
        for (int i = 0; i < m_expectedNumEntries; i++) {
            cells[i] = new Cell<IntegerData, IntegerData>(new IntegerData(0), new IntegerData(0), new IntegerData(0), hashFunction, 0);
        }

		m_iblt = new IBLT<IntegerData, IntegerData>(cells, new IntegerDataSubtablesHashFunctions(m_expectedNumEntries, m_hashFunctionCount));

		m_prefixes = new HashMap<String, Integer>();

		m_contentCacheForUserData = new MemoryContentCache(m_face); // default cleanup period is 1 second
		m_contentCacheForSyncData = new MemoryContentCache(m_face);
		
		addUserPrefix(userPrefix.toString());

		try {
			m_face.registerPrefix(new Name(syncPrefix).append("hello"),
					              onHelloInterest, onRegisterFailed);
			m_contentCacheForSyncData.registerPrefix(new Name(syncPrefix).append("sync"),
					                                 onRegisterFailed,
					                                 onPartialSyncInterest);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void appendIBLT (Name prefix) {
		byte[] table = new byte[m_iblt.getCells().length*4];

		int i = 0;
		for (Cell<IntegerData, IntegerData> cell : m_iblt.getCells()) {
			table[i] = (byte) cell.getCount();
			table[i+1] = (byte) cell.getKeySum().getValue();
			table[i+2] = (byte) cell.getValueSum().getValue();
			IntegerData integerData = (IntegerData) cell.getHashKeySum();
			table[i+3] = (byte) integerData.getValue();
			i++;
		}
		prefix.append(Integer.toString(m_iblt.getCells().length));
		prefix.append(table);
	}
	
	public void addUserPrefix(String prefix) {
		if (!m_prefixes.containsKey(prefix)) {
			m_prefixes.put(prefix, 0);
		}
		
		try {
			// Store the interest if no data yet - not sure if needed because this is for user data
			//m_contentCacheForUserData.registerPrefix(new Name(prefix), onRegisterFailed, m_contentCacheForUserData.getStorePendingInterest());
			m_contentCacheForUserData.registerPrefix(new Name(prefix), onRegisterFailed);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void publishData(Blob content, double freshness, String prefix) {
		if (!m_prefixes.containsKey(prefix)) {
			return;
		}
		
		int newSeq = m_prefixes.get(prefix) + 1;
		
		Name dataName = new Name(prefix);
		dataName.append(Integer.toString(newSeq));

        Data data = new Data(dataName);
        data.setContent(content);
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.setFreshnessPeriod(freshness);
        data.setMetaInfo(metaInfo);
        
        try {
			m_keyChain.sign(data);
		} catch (Exception e) {
			e.printStackTrace();
		}

        m_contentCacheForUserData.add(data);
        
        updateSeq(prefix, newSeq);
	}
	
	private void updateSeq(String prefix, int seq) {
		if (m_prefixes.containsKey(prefix) && m_prefixes.get(prefix) >= seq) {
			return;
		}
		
		if (m_prefixes.containsKey(prefix) && m_prefixes.get(prefix) != 0) {
			Integer hash = m_prefix2hash.get(prefix + "/" + m_prefixes.get(prefix));
			m_prefix2hash.remove(prefix + "/" + m_prefixes.get(prefix));
		    m_hash2prefix.remove(hash);
		    m_iblt.delete(new IntegerData(hash), new IntegerData(hash));
		}
		
		m_prefixes.put(prefix, seq);
		String prefixWithSeq = prefix + "/" + m_prefixes.get(prefix);
		
		// Hash of the data:
		com.google.common.hash.HashFunction hashImplementation = Hashing.murmur3_32();
		hashImplementation.hashString(prefixWithSeq, Charsets.UTF_8);
		Integer newHash = hashImplementation.hashCode();

		m_prefix2hash.put(prefixWithSeq, newHash);
		m_hash2prefix.put(newHash, prefix);
		m_iblt.insert(new IntegerData(newHash), new IntegerData(newHash));
		
		// Satisfy pending interest
		satisfyPendingSyncInterests(prefix);
	}
	
	private void satisfyPendingSyncInterests(String prefix) {
		for (PendingInterest interest: m_contentCacheForSyncData.getPendingInterestTable()) {
			Name interestName = interest.getInterest().getName();
			Component bfName = interestName.get(interestName.size()-3);
        	Component ibltName = interestName.get(interestName.size()-1);
        	ByteArrayInputStream in = null;
        	try {
				in.read(bfName.getValue().getImmutableArray());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        	int count = (int) interestName.get(interestName.size()-6).toNumber();
        	double false_positive = interestName.get(interestName.size()-5).toNumber() / 1000;

        	BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), count, false_positive);
        	try {
				bloomFilter.readFrom(in, Funnels.stringFunnel(Charsets.UTF_8));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
        	Name syncP = new Name("");
        	appendIBLT(syncP);
        	
        	Name otherIBLT = new Name("");
        	otherIBLT.append(ibltName);
        	
        	String content = "";
        	// IBF is different
        	if (syncP != otherIBLT) {
            	if (bloomFilter.mightContain(prefix)) {
            		content += prefix + "/" + m_prefixes.get(prefix) + "\n";
            	}
        	}
        	else { // IBF is same
        		continue;
        	}
        	
    		appendIBLT(interestName);
            Data data = new Data(interestName);
            data.setContent(new Blob(content));
            MetaInfo metaInfo = new MetaInfo();
            metaInfo.setFreshnessPeriod(m_syncReplyFreshness);
            data.setMetaInfo(metaInfo);
            try {
				m_keyChain.sign(data);
				m_contentCacheForSyncData.add(data);
            	System.out.println("Sent Sync data " + data);
			} catch (Exception e) {
				System.out.println("Error!");
				e.printStackTrace();
			}

		}
	}

    private final OnInterestCallback onHelloInterest = new OnInterestCallback() {
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
        	System.out.println("Received Hello Interest " + interest.getName());
        	String content = "";
            for (String prefix1 : m_prefixes.keySet()){
                content += prefix1 + "/" + m_prefixes.get(prefix1) + "\n";
            }

    		appendIBLT(prefix);
    		System.out.println(prefix);
            Data data = new Data(prefix);
            data.setContent(new Blob(content));
            MetaInfo metaInfo = new MetaInfo();
            metaInfo.setFreshnessPeriod(m_helloReplyFreshness);
            data.setMetaInfo(metaInfo);
            try {
				m_keyChain.sign(data);
				m_face.putData(data);
            	System.out.println("Sent Hello data " + data);
			} catch (Exception e) {
				System.out.println("Error!");
				e.printStackTrace();
			}
       }
	 };

     private final OnInterestCallback onPartialSyncInterest = new OnInterestCallback() {
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
        	System.out.println("Received sync interest: " + interest);
        	
        	Name interestName = interest.getName();
        	int ibfSize = (int) interestName.get(interestName.size()-2).toNumber();
        	Component ibltName = interestName.get(interestName.size()-1);
        	
        	int bfSize = (int) interestName.get(interestName.size()-4).toNumber();
        	Component bfName = interestName.get(interestName.size()-3);
        	ByteArrayInputStream in = null;
        	try {
				in.read(bfName.getValue().getImmutableArray());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        	int count = (int) interestName.get(interestName.size()-6).toNumber();
        	double false_positive = interestName.get(interestName.size()-5).toNumber() / 1000;

        	BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), count, false_positive);
        	try {
				bloomFilter.readFrom(in, Funnels.stringFunnel(Charsets.UTF_8));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
        	Name syncP = new Name("");
        	appendIBLT(syncP);
        	
        	Name otherIBLT = new Name("");
        	otherIBLT.append(ibltName);
        	
        	String content = "";
        	// IBF is different
        	if (syncP != otherIBLT) {
                for (String prefix1 : m_prefixes.keySet()) {
                	if (bloomFilter.mightContain(prefix1)) {
                		content += prefix1 + "/" + m_prefixes.get(prefix1) + "\n";
                	}
                }
        	}
        	else { // IBF is same
        		m_contentCacheForSyncData.storePendingInterest(interest, face);
        	}
        	
        	// append IBF to interestName
    		appendIBLT(interestName);
            Data data = new Data(interestName);
            data.setContent(new Blob(content));
            MetaInfo metaInfo = new MetaInfo();
            metaInfo.setFreshnessPeriod(m_syncReplyFreshness);
            data.setMetaInfo(metaInfo);
            try {
				m_keyChain.sign(data);
				m_face.putData(data);
            	System.out.println("Sent Sync data " + data);
			} catch (Exception e) {
				System.out.println("Error!");
				e.printStackTrace();
			}

    		/*Cell[] cells = new Cell[m_expectedNumEntries];
            HashFunction<IntegerData, IntegerData> hashFunction = new IntegerDataHashFunction();
            for (int i = 0; i < m_expectedNumEntries; i++) {
                cells[i] = new Cell<IntegerData, IntegerData>(new IntegerData(0), new IntegerData(0), new IntegerData(0), hashFunction, 0);
            }

        	IBLT<IntegerData, IntegerData> otherIblt = new IBLT<IntegerData, IntegerData>(cells, new IntegerDataSubtablesHashFunctions(m_expectedNumEntries, m_hashFunctionCount));;
        	byte[] table = ibltName.getValue().getImmutableArray();

        	int i = 0;
        	for (byte t : table) {
        		int count = table[i];
        		int keySum = table[i+1];
        		int valueSum = table[i+2];
        		int hashKeySum = table[i+3];
        		otherIblt.insert(new IntegerData(keySum), new IntegerData(valueSum));
        		i++;
        	}*/
        	
        	// if data not found call storePendingInterest(interest, face)
        }
	 };

	 private final OnRegisterFailed onRegisterFailed = new OnRegisterFailed() {
		public void onRegisterFailed(Name arg0) {
			System.out.println("Register failed for: " + arg0);
		}
	 };
	 
	private int m_expectedNumEntries;
	private Face m_face;
	private Name m_syncPrefix;
	private double m_syncReplyFreshness, m_helloReplyFreshness;
	private double m_syncInterestLifetime = 1000;
	private Map<String, Integer> m_prefixes;
	private Map<String, Integer> m_prefix2hash;
	private Map<Integer, String> m_hash2prefix;
	private KeyChain m_keyChain;
	private MemoryContentCache m_contentCacheForUserData;
	private MemoryContentCache m_contentCacheForSyncData;
	
	private static int m_hashFunctionCount = 3;
	
	private IBLT<IntegerData, IntegerData> m_iblt;
}

