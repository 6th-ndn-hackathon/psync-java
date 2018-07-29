package ndn.psync.java_psync;

import ndn.psync.java_psync.detail.BloomFilter;
import ndn.psync.java_psync.detail.IBLT;
import ndn.psync.java_psync.detail.IBLT.ListResult;
import ndn.psync.java_psync.detail.State;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.MetaInfo;
import net.named_data.jndn.Name;
import net.named_data.jndn.Name.Component;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.MemoryContentCache;

public class PartialProducer extends ProducerBase {
	private MemoryContentCache m_contentCacheForSyncData;

	public PartialProducer(int expectedNumEntries, Face face, Name syncPrefix, Name userPrefix,
			               double syncReplyFreshness, double helloReplyFreshness, KeyChain keyChain) {
		super(expectedNumEntries, face, syncPrefix, userPrefix, syncReplyFreshness, helloReplyFreshness, keyChain);

		try {
			m_face.registerPrefix(new Name(syncPrefix).append("hello"),
					              onHelloInterest, onRegisterFailed);
			m_contentCacheForSyncData.registerPrefix(new Name(syncPrefix).append("sync"),
					                                 onRegisterFailed,
					                                 onSyncInterest);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    private final OnInterestCallback onHelloInterest = new OnInterestCallback() {
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
        	System.out.println("Sync Interest Received, nonce: " + interest.getNonce());
        	Name interestName = interest.getName();

        	Component bfName, ibltName;
        	long projectedCount;
        	double falsePositiveProb;

        	try {
        		projectedCount = interestName.get(interestName.size()-4).toNumber();
        		falsePositiveProb = interestName.get(interestName.size()-3).toNumber()/1000.;
        		bfName = interestName.get(interestName.size()-2);

        		ibltName = interestName.get(interestName.size()-1);
        	}
        	catch (Exception e) {
        		System.out.println("Cannot extract bloom filter and IBF from sync interest: " + e.getMessage());
        		System.out.println("Format: /<syncPrefix>/sync/<BF-count>/<BF-false-positive-probability>/<BF>/<IBF>");
        		return;
        	}

        	BloomFilter bf;
        	IBLT iblt = new IBLT(m_expectedNumEntries);

        	try {
        		bf = new BloomFilter(projectedCount, falsePositiveProb, bfName);
        		iblt.initialize(ibltName);
        	}
        	catch (Exception e) {
        		System.out.println(e.toString());
        		return;
        	}

        	// get the difference
        	IBLT diff = m_iblt.subtract(iblt);
        	ListResult listResult = diff.listEntries();

        	if (!listResult.success) {
        		System.out.println("Can't decode the difference, sending application Nack");
        		sendApplicationNack(interestName);
        		return;
        	}

        	// generate content for Sync reply
        	State state = new State();
        	for (long hash : listResult.positive) {        		
       			Name hashPrefix = m_hash2prefix.get(hash);
      			if (bf.contains(hashPrefix.toUri())) {
      				state.addContent(new Name(hashPrefix).append(Component.fromNumber(m_prefixes.get(hashPrefix))));
        		}
        	}
        }
    };

      /*

      NDN_LOG_TRACE("m_threshold: " << m_threshold << " Total: " << positive.size() + negative.size());

      if (positive.size() + negative.size() >= m_threshold || !state.getContent().empty()) {

        // send back data
        ndn::Name syncDataName = interestName;
        m_iblt.appendToName(syncDataName);
        ndn::Data data;
        data.setName(syncDataName);
        data.setFreshnessPeriod(m_syncReplyFreshness);
        data.setContent(state.wireEncode());

        m_keyChain.sign(data);
        NDN_LOG_DEBUG("Sending sync data");
        m_face.put(data);

        return;
      }

      ndn::util::scheduler::ScopedEventId scopedEventId(m_scheduler);
      auto it = m_pendingEntries.emplace(interestName,
                                         PendingEntryInfo{bf, iblt, std::move(scopedEventId)});

      it.first->second.expirationEvent =
        m_scheduler.scheduleEvent(interest.getInterestLifetime(),
                                  [this, interest] {
                                    NDN_LOG_TRACE("Erase Pending Interest " << interest.getNonce());
                                    m_pendingEntries.erase(interest.getName());
                                  });
    }
       }
	 };
	 
	 private final OnInterestCallback onSyncInterest = new OnInterestCallback() {
		 public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                                InterestFilter filterData) {
        	
		 }
	 };*/
        
        
        private final OnInterestCallback onSyncInterest = new OnInterestCallback() {
        	public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
        						   InterestFilter filterData) {
        	
        	}
        };
}
