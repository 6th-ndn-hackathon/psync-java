import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.NetworkNack;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnNetworkNack;
import net.named_data.jndn.OnTimeout;
import java.util.*;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LogicConsumer {

    private Name m_syncPrefix;
    private Face m_face;
    private ReceiveHelloCallback m_onReceivedHelloData;
    private ReceiveSyncCallback m_onReceivedSyncData;
    // private UpdateCallback m_onUpdate;
    private int m_count;
    private double m_false_positive;
    private Name m_ibltName;
    private Map <String, Integer> m_prefixes;
    private boolean m_helloSent;
    private Set<String> m_sl;
    BloomFilter<String> m_bloomFilter;
    private long m_outstandingInterestId;
    public LogicConsumer(Name prefix,
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
        m_helloSent = false;
       m_bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), m_count, m_false_positive);
    }

    public interface ReceiveHelloCallback {
        void onReceivedHelloData(String content);
    }
    
    public interface ReceiveSyncCallback {
        void onReceivedSyncData(String content);
    }

    public void sendHelloInterest() {
        Name helloInterestName = m_syncPrefix;
        helloInterestName.append("hello");

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

            m_ibltName = helloDataName.getSubName(helloDataName.size()-2, 2);

            String content = new String(data.getContent().getImmutableArray());

            m_helloSent = true;

            m_onReceivedHelloData.onReceivedHelloData(content);
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
    	Name syncInterestName = m_syncPrefix;
        syncInterestName.append("sync");
        
        // Append subscription list
        appendBF(syncInterestName);
        
        syncInterestName.append(m_ibltName);
        Interest syncInterest = new Interest(syncInterestName);
        syncInterest.setInterestLifetimeMilliseconds(4000);
        syncInterest.setMustBeFresh(true);

        System.out.println("Send sync interest " + syncInterest);

        try {
        	m_outstandingInterestId = m_face.expressInterest(syncInterest,
				                                             onSyncDataCallback,
				                                             onSyncTimeout,
				                                             onSyncNack);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void appendBF(Name prefix) {
    	prefix.append(Integer.toString(m_count));
    	prefix.append(Integer.toString((int) m_false_positive*1000));

    	ByteArrayOutputStream out = null;
    	try {
			m_bloomFilter.writeTo(out);
			prefix.append(out.toByteArray());
			prefix.append(Integer.toString(out.size()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    boolean haveSentHello(){
        return m_helloSent;
    }

    public Set<String> getSL(){
        return m_sl;
    }

    public void addSL(String s) {
        m_prefixes.put(s, 0);
        m_sl.add(s);
        m_bloomFilter.put(s);
    }
    
    private OnData onSyncDataCallback = new OnData() {
        public void onData(Interest interest, Data data) {
        	Name syncDataName = data.getName();
        	m_ibltName = syncDataName.getSubName(syncDataName.size()-2, 2);
        	
        	String newContent;
        	String content = new String(data.getContent().getImmutableArray());
        	String[] csplit = content.split("\n");
        	
        	for (String t : csplit) {
        		
        		for (String key : m_prefixes.keySet()){
                    if (t.split("/")[0] == key && Integer.parseInt(t.split("/")[1]) > m_prefixes.get(key)) {
                    	newContent += t.split("/")[0] + "/" + t.split("/")[1] + "\n";
                    }
                }
        	}
        	m_onReceivedSyncData.onReceivedSyncData(newContent);
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

