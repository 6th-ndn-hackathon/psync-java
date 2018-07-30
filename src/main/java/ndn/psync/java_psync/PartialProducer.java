package ndn.psync.java_psync;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static java.util.concurrent.TimeUnit.*;

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
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.tpm.TpmBackEnd.Error;
import net.named_data.jndn.util.Blob;

public class PartialProducer extends ProducerBase {
	private class
	PendingInterestInfo {
		public IBLT iblt;
		public BloomFilter bf;
		public final ScheduledExecutorService expiryEvent = Executors.newScheduledThreadPool(1);;
	}
	private Map<Name, PendingInterestInfo> m_pendingEntries = new HashMap<Name, PendingInterestInfo>();

	public PartialProducer(int expectedNumEntries, Face face, Name syncPrefix, Name userPrefix,
			               double syncReplyFreshness, double helloReplyFreshness, KeyChain keyChain) {
		super(expectedNumEntries, face, syncPrefix, userPrefix, syncReplyFreshness, helloReplyFreshness, keyChain);

		try {
			m_face.registerPrefix(syncPrefix, onInterest, onRegisterFailed);
			m_face.setInterestFilter(new InterestFilter(new Name(syncPrefix).append("hello")), onHelloInterest);
			m_face.setInterestFilter(new InterestFilter(new Name(syncPrefix).append("sync")), onSyncInterest);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private final OnInterestCallback onInterest = new OnInterestCallback() {
		public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
				InterestFilter filter) {
			System.out.print("Received interest: " + interest);
		}
	};

	private final OnInterestCallback onHelloInterest = new OnInterestCallback() {
		public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
							   InterestFilter filterData) {
			  System.out.println("Hello Interest Received, nonce: " + interest.getNonce());

			  State state = new State();

			  for (Name content : m_prefixes.keySet()) {
				  state.addContent(content);
			  }
			  System.out.println("sending content p: " + state);

			  Name helloDataName = prefix;
			  m_iblt.appendToName(helloDataName);

			  Data data = new Data();
			  data.setName(helloDataName);
			  MetaInfo metaInfo = new MetaInfo();
              metaInfo.setFreshnessPeriod(m_helloReplyFreshness);
              data.setMetaInfo(metaInfo);
			  data.setContent(state.wireEncode());

			  try {
				  m_keyChain.sign(data);
			  } catch (SecurityException e) {
				  // TODO Auto-generated catch block
				  e.printStackTrace();
			  } catch (Error e) {
				  // TODO Auto-generated catch block
				  e.printStackTrace();
			  } catch (net.named_data.jndn.security.pib.PibImpl.Error e) {
				  // TODO Auto-generated catch block
				  e.printStackTrace();
			  } catch (net.named_data.jndn.security.KeyChain.Error e) {
				  // TODO Auto-generated catch block
				  e.printStackTrace();
			  }

			  try {
				  m_face.putData(data);
			  } catch (IOException e) {
				  // TODO Auto-generated catch block
				  e.printStackTrace();
			  }
		}
	};

    private final OnInterestCallback onSyncInterest = new OnInterestCallback() {
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
        	System.out.println("Sync Interest Received, nonce: " + interest.getNonce());
        	final Name interestName = interest.getName();

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
        	
        	if (listResult.positive.size() + listResult.negative.size() >= m_threshold ||
        		!state.getContent().isEmpty()) {

                // send back data
                Name syncDataName = interestName;
                m_iblt.appendToName(syncDataName);
                Data data = new Data();
                data.setName(syncDataName);
                MetaInfo metaInfo = new MetaInfo();
                metaInfo.setFreshnessPeriod(m_syncReplyFreshness);
                data.setMetaInfo(metaInfo);
                data.setContent(state.wireEncode());
                try {
					m_keyChain.sign(data);
				} catch (SecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Error e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (net.named_data.jndn.security.pib.PibImpl.Error e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (net.named_data.jndn.security.KeyChain.Error e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                System.out.println("Sending sync data");
                try {
					m_face.putData(data);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                return;
        	}
        	
        	PendingInterestInfo entry = new PendingInterestInfo();
        	entry.iblt = iblt;
        	entry.bf = bf;

        	m_pendingEntries.putIfAbsent(interestName, entry);
        	entry.expiryEvent.schedule(new Runnable() {
        								   public void run() {
        									   System.out.println("Deleting pending interesr");
        									  m_pendingEntries.remove(interestName);
        								   }
        							   },
        			                   (long) interest.getInterestLifetimeMilliseconds(),
        							   MILLISECONDS);
        }
    };
    
	public void
	publishName(Blob content, double freshness, Name prefix, Long seq) {
		if (!m_prefixes.containsKey(prefix)) {
			return;
		}

		long newSeq;
		if (seq != null) {
			newSeq = seq;
		} else {
			newSeq = m_prefixes.get(prefix) + 1;
		}
		
		Name dataName = new Name(prefix);
		dataName.append(Component.fromNumber(newSeq));
        
		updateSeqNo(prefix, newSeq);
		
		satisfyPendingSyncInterests(prefix);
	}
	
	private void
	satisfyPendingSyncInterests(Name prefix) {
		for (Name interestName: m_pendingEntries.keySet()) {
    		PendingInterestInfo entry = m_pendingEntries.get(interestName);
    		BloomFilter bloomFilter = entry.bf;
    		IBLT iblt = entry.iblt;

        	IBLT diff = m_iblt.subtract(iblt);
        	ListResult listResult = diff.listEntries();
        	if (!listResult.success) {
        		m_pendingEntries.remove(interestName);
        		continue;
        	}
        	
        	State state = new State();
            if (bloomFilter.contains(prefix.toUri()) ||
            	listResult.positive.size() + listResult.negative.size() >= m_threshold) {
              if (bloomFilter.contains(prefix.toUri())) {
                 state.addContent(new Name(prefix).append(Component.fromNumber(m_prefixes.get(prefix))));
              }
              else {
                System.out.println("Sending with empty content to send latest IBF to consumer");
              }

              Name syncDataName = interestName;
              m_iblt.appendToName(syncDataName);
              Data data = new Data();
              data.setName(syncDataName);
              MetaInfo metaInfo = new MetaInfo();
              metaInfo.setFreshnessPeriod(m_syncReplyFreshness);
              data.setMetaInfo(metaInfo);
              data.setContent(state.wireEncode());

              try {
            	  m_keyChain.sign(data);
              } catch (SecurityException e) {
            	  // TODO Auto-generated catch block
            	  e.printStackTrace();
              } catch (Error e) {
            	  // TODO Auto-generated catch block
            	  e.printStackTrace();
              } catch (net.named_data.jndn.security.pib.PibImpl.Error e) {
            	  // TODO Auto-generated catch block
            	  e.printStackTrace();
              } catch (net.named_data.jndn.security.KeyChain.Error e) {
            	  // TODO Auto-generated catch block
            	  e.printStackTrace();
              }

              System.out.println("Sending sync data");

              try {
            	  m_face.putData(data);
              } catch (IOException e) {
            	  // TODO Auto-generated catch block
            	  e.printStackTrace();
              }
              m_pendingEntries.remove(interestName);
            }
		}
	}
}
