package ndn.psync.java_psync;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.NetworkNack;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnNetworkNack;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;

import java.io.IOException;
import java.util.*;

import ndn.psync.java_psync.detail.BloomFilter;
import ndn.psync.java_psync.detail.MissingDataInfo;
import ndn.psync.java_psync.detail.State;

public class Consumer {

    private Name m_syncPrefix;
    private Face m_face;
    private ReceiveHelloCallback m_onReceivedHelloData;
    private ReceiveSyncCallback m_onReceivedSyncData;
    private int m_count;
    private double m_false_positive;
    private Name m_ibltName;
    private Map <Name, Long> m_prefixes = new HashMap<Name, Long>();
    private Set<Name> m_subscriptionList = new HashSet<Name>();
    BloomFilter m_bloomFilter;
    private long m_outstandingInterestId = 0;

    public Consumer(Name prefix,
                    Face face,
                    ReceiveHelloCallback onReceivedHelloData,
                    ReceiveSyncCallback onReceivedSyncData,
                    int count,
                    double false_positive)
    {
    	m_syncPrefix = prefix;
        m_face = face;
        m_onReceivedHelloData = onReceivedHelloData;
        m_onReceivedSyncData = onReceivedSyncData;
        m_count = count;
        m_false_positive = false_positive;
       m_bloomFilter = new BloomFilter(m_count, m_false_positive);
    }

    public interface ReceiveHelloCallback {
        void onReceivedHelloData(ArrayList<Name> content);
    }

    public interface ReceiveSyncCallback {
        void onReceivedSyncData(ArrayList<MissingDataInfo> updates);
    }

    public void sendHelloInterest() {
        Name helloInterestName = new Name(m_syncPrefix).append("hello");

        Interest helloInterest = new Interest(helloInterestName);
        helloInterest.setInterestLifetimeMilliseconds(4000);
        helloInterest.setMustBeFresh(true);

        System.out.println("Send hello interest " + helloInterest);

        try {
            m_face.expressInterest(helloInterest,
                                   onHelloDataCallback,
                                   onHelloTimeout,
                                   onHelloNack);
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    public interface OnNackForHelloCallback {
        public void onNack();
    }

    private OnData onHelloDataCallback = new OnData() {
        public void onData(Interest interest, Data data) {
            Name helloDataName = data.getName();

            m_ibltName = helloDataName.getSubName(helloDataName.size()-1, 1);

            State state = new State();
            try {
				state.wireDecode(data.getContent().buf());
			} catch (EncodingException e) {
				e.printStackTrace();
			}

            m_onReceivedHelloData.onReceivedHelloData(state.getContent());
        }
    };

    private OnTimeout onHelloTimeout = new OnTimeout() {
        public void onTimeout(Interest interest) {
            System.out.println("Timeout for interest " + interest.getName().toString());
            sendHelloInterest();
        }
    };

    private OnNetworkNack onHelloNack = new OnNetworkNack() {
		public void onNetworkNack(Interest interest, NetworkNack networkNack) {
			System.out.println("Nack");
		}
    };

    public void sendSyncInterest() {
    	// Sync interest format for partial: /<sync-prefix>/sync/<BF>/<old-IBF>
    	Name syncInterestName = new Name(m_syncPrefix).append("sync");
        
        // Append subscription list
        m_bloomFilter.appendToName(syncInterestName);
        
        syncInterestName.append(m_ibltName);
        Interest syncInterest = new Interest(syncInterestName);
        syncInterest.setInterestLifetimeMilliseconds(4000);
        syncInterest.setMustBeFresh(true);

        System.out.println("Send sync interest " + syncInterest);

        try {
        	if (m_outstandingInterestId != 0) {
        		m_face.removePendingInterest(m_outstandingInterestId);
        	}

        	m_outstandingInterestId = m_face.expressInterest(syncInterest,
				                                             onSyncDataCallback,
				                                             onSyncTimeout,
				                                             onSyncNack);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<Name> getSL(){
        return m_subscriptionList;
    }

    public void addSL(Name s) {
        m_prefixes.put(s, (long) 0);
        m_subscriptionList.add(s);
        m_bloomFilter.insert(s.toUri());
    }
    
    private OnData onSyncDataCallback = new OnData() {
        public void onData(Interest interest, Data data) {
        	Name syncDataName = data.getName();
        	m_ibltName = syncDataName.getSubName(syncDataName.size()-1, 1);

        	State state = new State();
        	try {
				state.wireDecode(data.getContent().buf());
			} catch (EncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        	ArrayList<MissingDataInfo> updates = new ArrayList<MissingDataInfo>();
        	MissingDataInfo update = new MissingDataInfo();
        	for (Name content : state.getContent()) {
        		Name prefix = content.getPrefix(-1);
        		long seq = content.get(content.size()-1).toNumber();
        		if (!m_prefixes.containsKey(prefix) || seq > m_prefixes.get(prefix)) {
          	        // If this is just the next seq number then we had already informed the consumer about
        	        // the previous sequence number and hence seq low and seq high should be equal to current seq
        	    	update.prefix = prefix;
        	    	update.lowSeq = m_prefixes.get(prefix) + 1;
        	    	update.highSeq = seq;
        	        updates.add(update);
        	    }
        	}
        	
        	m_onReceivedSyncData.onReceivedSyncData(updates);
        	sendSyncInterest();
        }
    };
    
    private OnTimeout onSyncTimeout = new OnTimeout() {
        public void onTimeout(Interest interest) {
            System.out.println("Timeout for interest " + interest.getName().toString());
            sendSyncInterest();
        }
    };
            
    private OnNetworkNack onSyncNack = new OnNetworkNack() {
		public void onNetworkNack(Interest interest, NetworkNack networkNack) {
			System.out.println("Nack");
		}
    };
}