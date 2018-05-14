package com.example.damian.partialsyncapp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.KeyType;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.transport.TcpTransport;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.SegmentFetcher;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    MemoryIdentityStorage identityStorage;
    MemoryPrivateKeyStorage privateKeyStorage;
    IdentityManager identityManager;
    KeyChain keyChain;
    public Face face;
    SegmentFetcher fetcher;
    LogicConsumer consumer;
    private boolean has_security = false;
    private String helloContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setup_security();
    }

    // Use logic consumer to send /hello, /partial, and /sync

    public void sendInitialHello(View view) {
        EditText prefixText = (EditText) findViewById(R.id.syncPrefixText);
        String prefix = prefixText.getText().toString();
        if (!has_security) {
            setup_security();
        }
        if (prefix.isEmpty()) {
            Toast.makeText(this, "No prefix provided!", Toast.LENGTH_LONG).show();
        } else {
            if (face == null) {
                Toast.makeText(this, "Face is null", Toast.LENGTH_LONG).show();
            } else {
                // String hprefix = prefix + "/hello";
                final Name helloPrefix = new Name(prefix);
                NetThread thread = new NetThread(helloPrefix);
                thread.start();
            }
        }
        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        // test --> helloContent = "/ndn/test /ndn/stuff";
        if (helloContent != null && !helloContent.isEmpty()) {
            updateList();
        }
    }

    private void updateList() {
        Log.d("updateList", "Updated list with content: " + helloContent);
        List<String> spinnerList =  new ArrayList<String>();
        String[] list = helloContent.split("\\r?\\n");
        for (String s : list) {
            spinnerList.add(s);
        }
        helloContent = "";
        // spinnerList.add(helloContent);
        final Spinner spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<String> adp1 = new ArrayAdapter<String>(MainActivity.this,
                                    android.R.layout.simple_spinner_dropdown_item, spinnerList);
        adp1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adp1);
    }

    public void setup_security() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                face = new Face();
                identityStorage = new MemoryIdentityStorage();
                privateKeyStorage = new MemoryPrivateKeyStorage();
                identityManager = new IdentityManager(identityStorage, privateKeyStorage);
                keyChain = new KeyChain(identityManager);
                keyChain.setFace(face);

                has_security = true;
                Log.d("sendInitialHello", "Made it past security.");
            }
        });
        thread.run();
    }

    private final LogicConsumer.ReceiveHelloCallback onReceivedHello = new LogicConsumer.ReceiveHelloCallback() {
        @Override
        public void onReceivedHelloData(String content) {
            Log.d("OnReceivedHelloData", "We received this content: " + content);
            helloContent = content;
        }
    };

    public void sendSync(View view) {

    }

    public void processData(Data[] dataArray) {
        List<String> spinnerArray =  new ArrayList<String>();
        for(Data d : dataArray) {
            spinnerArray.add(d.getName().toString());
        }
    }

    private class NetThread extends Thread {
        Name helloPrefix;
        public NetThread(Name hp) {
            helloPrefix = hp;
        }

        @Override
        public void run() {
            consumer = new LogicConsumer(helloPrefix, face, onReceivedHello, 4, 0.001);
            consumer.sendHelloInterest();
            try {
                while (true) {
                    face.processEvents();

                    // We need to sleep for a few milliseconds so we don't use 100% of
                    //   the CPU.
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                face.shutdown();
                System.out.println("My Exception: " + e.toString());
                return;
            }
        }
    }
}
