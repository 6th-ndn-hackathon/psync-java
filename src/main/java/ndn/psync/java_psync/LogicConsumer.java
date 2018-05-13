/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ndn.psync.java_psync;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.MemoryContentCache;
import net.named_data.jndn.sync.ChronoSync2013;

import java.util.*;
import java.io.IOException;

public class LogicConsumer {
    
    private Name m_syncPrefix;
    private Face m_face;
    // private ReceiveHelloCallback m_onRecieveHelloData;
    // private UpdateCallback m_onUpdate;
    private int m_count;
    private double m_false_positive;
    private boolean m_suball;
    private Name m_iblt;
    private Map <String, Integer> m_prefixes;
    private boolean m_helloSent;
    private Set<String> m_sl;
    ArrayList<String> m_ns;
    // private bloom_filter m_bf;
    //private final PendingInterestId m_outstandingInterestId;
    // private Scheduler m_scheduler;

    // private boost::mt19937 m_randomGenerator;
    // private boost::variate_generator<boost::mt19937&, boost::uniform_int<> > m_rangeUniformRandom;
    
    public LogicConsumer(Name prefix,
	                 Face face,
	                 //ReceiveHelloCallback onReceivedHelloData,
	                 //UpdateCallback onUpdate,
	                 int count,
	                 double false_positive)
    {
	m_syncPrefix = prefix;
        m_face = face;
        // m_onReceiveHelloData = onReceivedHelloData;
        // m_onUpdate = onUpdate;
        m_count = count;
        m_false_positive = false_positive;
        m_suball = (false_positive == 0.001 && m_count == 1);
        m_helloSent = false;
        // m_scheduler
        // m_randomGenerator
        // m_rangeUniformRandom
        /* bloom_parameters opt;
           opt.false_positive_probability = m_false_positive;
           opt.projected_element_count = m_count;
           opt.compute_optimal_parameters();
           m_bf = bloom_filter(opt);
        */
    }
    
    public void stop() {
        m_face.shutdown();
    }
    
    public void sendHelloInterest() {
        Name helloInterestName = m_syncPrefix;
        helloInterestName.append("hello");

        Interest helloInterest = new Interest(helloInterestName);
        helloInterest.setInterestLifetimeMilliseconds(4000);
        helloInterest.setMustBeFresh(true);

        /*_LOG_DEBUG("Send Hello Interest " << helloInterest << " Nonce " << helloInterest.getNonce() <<
             " mustbefresh: " << helloInterest.getMustBeFresh());*/

        try {
            m_face.expressInterest(helloInterest,
                                   onHelloDataCallback,
                                   onHelloTimeout);
                                   //null);
                               // onNackForHello);
                               /* bind(&LogicConsumer::onHelloData, this, _1, _2),
                               bind(&LogicConsumer::onNackForHello, this, _1, _2),
                               bind(&LogicConsumer::onHelloTimeout, this, _1));*/
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /*public interface OnHelloDataCallback {
        public void onData(Interest interest, Data data);
    }*/
    
    /*public interface OnHelloTimeoutCallback {
        public void onTimeout(Interest interest);
    }*/
    
    public interface OnNackForHelloCallback {
        public void onNack();
    }
    
    private OnData onHelloDataCallback = new OnData() {
        @Override
        public void onData(Interest interest, Data data) {
            Name helloDataName = data.getName();

            // _LOG_DEBUG("On Hello Data " << helloDataName);

            m_iblt = helloDataName.getSubName(helloDataName.size()-2, 2);
            // _LOG_DEBUG("m_iblt: " << m_iblt);
            /* String content = (reinterpret_cast<const char*>(data.getContent().value()),
                        data.getContent().value_size()); */
                        
            String content = new String(data.getContent().getImmutableArray());

            m_helloSent = true;

            // m_onReceiveHelloData(content);
        }
    };

            
    private OnTimeout onHelloTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {
            System.out.println("Timeout for interest " + interest.getName().toString());
        }
    };
            
    private OnNackForHelloCallback onNackForHello = new OnNackForHelloCallback() {
        @Override
        public void onNack() {
            System.out.println("Nack");
        }
    };
    
    /*public void sendSyncInterest() {
        // Sync interest format for partial: /<sync-prefix>/sync/<BF>/<old-IBF>
        // Sync interest format for full: /<sync-prefix>/sync/full/<old-IBF>?

        // name last component is the IBF and content should be the prefix with the version numbers
        assert(m_helloSent);
        assert(!m_iblt.empty());

        Name syncInterestName = m_syncPrefix;
        syncInterestName.append("sync");

        // Append subscription list
        appendBF(syncInterestName);

        // Append IBF received in hello/sync data
        syncInterestName.append(m_iblt);

        Interest syncInterest(syncInterestName);
        // Need 4000 in constant (and configurable by the user?)
        syncInterest.setInterestLifetime(ndn::time::milliseconds(4000));
        syncInterest.setMustBeFresh(true);

        /* _LOG_DEBUG("sendSyncInterest lifetime: " << syncInterest.getInterestLifetime()
              << " nonce=" << syncInterest.getNonce()); 

       // Remove last pending interest before sending a new one
       if (m_outstandingInterestId != 0) {
            m_face.removePendingInterest(m_outstandingInterestId);
            m_outstandingInterestId = 0;
        }

        m_outstandingInterestId = m_face.expressInterest(syncInterest,
                            bind(&LogicConsumer::onSyncData, this, _1, _2),
                            bind(&LogicConsumer::onNackForSync, this, _1, _2),
                            bind(&LogicConsumer::onSyncTimeout, this, _1));
    }*/
    
    boolean haveSentHello(){
        return m_helloSent;
    }

    public Set<String> getSL(){
        return m_sl;
    }

    public void addSL(String s) {
        m_prefixes.put(s, 0);
        m_sl.add(s);
        // m_bf.insert(s);
    }

    public ArrayList<String> getNS()
    {
        return m_ns;
    }

    /*public void appendBF(Name name) {
        name.append("" + m_count);
        name.append("" + (int)(m_false_positive*1000));
        name.append((m_bf.getTableSize()).toString());
        for(data : m_bf) {
            name.append(data);
        }
    }*/

    //public void onData(Interest interest, Data data, FetchDataCallback fdCallback) {
        // Name dataName = data.getName();
        /*_LOG_INFO("On Data " << dataName.getSubName(1, dataName.size()-2) << "/"
             << dataName.get(dataName.size()-1).toNumber());*/
        //fdCallback(data);
    //}
    
    /*public void onDataTimeout(Interest interest, int nRetries, FetchDataCallBack fdCallback) {
        if (nRetries <= 0) {
            return;
        }

        // _LOG_INFO("Data timeout for " << interest);
        Interest newNonceInterest = interest;
        newNonceInterest.refreshNonce();
        m_face.expressInterest(newNonceInterest,
                         bind(&LogicConsumer::onData, this, _1, _2, fdCallback),
                         bind(&LogicConsumer::onDataNack, this, _1, _2, nRetries - 1, fdCallback),
                         bind(&LogicConsumer::onDataTimeout, this, _1, nRetries - 1, fdCallback));
    }*/
    
    // java's lp::Nack?
    //public void onDataNack(Interest interest, const ndn::lp::Nack& nack, int nRetries,
    //                      FetchDataCallBack& fdCallback) {
        /*_LOG_INFO("received Nack with reason " << nack.getReason()
             << " for interest " << interest << std::endl);*/
        // Re-send after a while; Here is where you'll have to make a scheduler
        /*ndn::time::steady_clock::Duration after = ndn::time::milliseconds(50);
        m_scheduler.scheduleEvent(after, std::bind(&LogicConsumer::onDataTimeout, this, interest,
                                             nRetries - 1, fdCallback));*/
    //}
    
    /*public void printSL() {
        String sl = "";
        for (String s : m_sl) {
            sl += s + " ";
        }
        //_LOG_INFO("Subscription List: " << sl);
    }*/
    
    // private onReceiveHelloCallback
}