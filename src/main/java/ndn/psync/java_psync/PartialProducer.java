package ndn.psync.java_psync;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.MetaInfo;
import net.named_data.jndn.Name;
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
        	/*System.out.println("Received Hello Interest " + interest.getName());
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
			}*/
       }
	 };
	 
	 private final OnInterestCallback onSyncInterest = new OnInterestCallback() {
		 public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                                InterestFilter filterData) {
        	
		 }
	 };
}
