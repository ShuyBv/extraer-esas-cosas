package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

import android.content.*;
import android.os.*;
import java.io.*;
import android.net.*;
import android.nfc.*;
import android.nfc.tech.*;
import android.util.*;
import android.app.*;
import java.util.*;

public class NfcSensor {
    private static NfcSensor sInstance;
    private final Context mContext;
    private final NfcAdapter mNfcAdapter;
    private final Object mTagLock;
    private final List<ListenerHelper> mListeners;
    private IntentFilter[] mNfcIntentFilters;
    private Ndef mCurrentNdef;
    private Tag mCurrentTag;
    private boolean mCurrentTagIsCardboard;
    private Timer mNfcDisconnectTimer;
    private int mTagConnectionFailures;
    
    public static NfcSensor getInstance(final Context context) {
        if (NfcSensor.sInstance == null) {
            NfcSensor.sInstance = new NfcSensor(context);
        }
        return NfcSensor.sInstance;
    }
    
    private NfcSensor(final Context context) {
        super();
        this.mContext = context.getApplicationContext();
        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(this.mContext);
        this.mListeners = new ArrayList<ListenerHelper>();
        this.mTagLock = new Object();
        if (this.mNfcAdapter == null) {
            return;
        }
        final IntentFilter ndefIntentFilter = new IntentFilter("android.nfc.action.NDEF_DISCOVERED");
        ndefIntentFilter.addAction("android.nfc.action.TECH_DISCOVERED");
        ndefIntentFilter.addAction("android.nfc.action.TAG_DISCOVERED");
        this.mNfcIntentFilters = new IntentFilter[] { ndefIntentFilter };
        this.mContext.registerReceiver((BroadcastReceiver)new BroadcastReceiver() {
            public void onReceive(final Context context, final Intent intent) {
                NfcSensor.this.onNfcIntent(intent);
            }
        }, ndefIntentFilter);
    }
    
    public void addOnCardboardNfcListener(final OnCardboardNfcListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (this.mListeners) {
            for (final ListenerHelper helper : this.mListeners) {
                if (helper.getListener() == listener) {
                    return;
                }
            }
            this.mListeners.add(new ListenerHelper(listener, new Handler()));
        }
    }
    
    public void removeOnCardboardNfcListener(final OnCardboardNfcListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (this.mListeners) {
            for (final ListenerHelper helper : this.mListeners) {
                if (helper.getListener() == listener) {
                    this.mListeners.remove(helper);
                }
            }
        }
    }
    
    public boolean isNfcSupported() {
        return this.mNfcAdapter != null;
    }
    
    public boolean isNfcEnabled() {
        return this.isNfcSupported() && this.mNfcAdapter.isEnabled();
    }
    
    public boolean isDeviceInCardboard() {
        synchronized (this.mTagLock) {
            return this.mCurrentTagIsCardboard;
        }
    }
    
    public NdefMessage getTagContents() {
        synchronized (this.mTagLock) {
            return (this.mCurrentNdef != null) ? this.mCurrentNdef.getCachedNdefMessage() : null;
        }
    }

    public void onResume(final Activity activity) {
        if (!this.isNfcEnabled()) {
            return;
        }
        final Intent intent = new Intent("android.nfc.action.NDEF_DISCOVERED");
        intent.setPackage(activity.getPackageName());
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
        this.mNfcAdapter.enableForegroundDispatch(activity, pendingIntent, this.mNfcIntentFilters, (String[][])null);
    }
    
    public void onPause(final Activity activity) {
        if (!this.isNfcEnabled()) {
            return;
        }
        this.mNfcAdapter.disableForegroundDispatch(activity);
    }
    
    public void onNfcIntent(final Intent intent) {
        if (!this.isNfcEnabled() || intent == null || !this.mNfcIntentFilters[0].matchAction(intent.getAction())) {
            return;
        }
        this.onNewNfcTag((Tag)intent.getParcelableExtra("android.nfc.extra.TAG"));
    }
    
    private void onNewNfcTag(final Tag nfcTag) {
        if (nfcTag == null) {
            return;
        }
        synchronized (this.mTagLock) {
            final Tag previousTag = this.mCurrentTag;
            final Ndef previousNdef = this.mCurrentNdef;
            final boolean previousTagWasCardboard = this.mCurrentTagIsCardboard;
            this.closeCurrentNfcTag();
            this.mCurrentTag = nfcTag;
            this.mCurrentNdef = Ndef.get(nfcTag);
            if (this.mCurrentNdef == null) {
                if (previousTagWasCardboard) {
                    this.sendDisconnectionEvent();
                }
                return;
            }
            boolean isSameTag = false;
            if (previousNdef != null) {
                final byte[] tagId1 = this.mCurrentTag.getId();
                final byte[] tagId2 = previousTag.getId();
                isSameTag = (tagId1 != null && tagId2 != null && Arrays.equals(tagId1, tagId2));
                if (!isSameTag && previousTagWasCardboard) {
                    this.sendDisconnectionEvent();
                }
            }
            NdefMessage nfcTagContents;
            try {
                this.mCurrentNdef.connect();
                nfcTagContents = this.mCurrentNdef.getCachedNdefMessage();
            }
            catch (Exception e) {
                final String s = "NfcSensor";
                final String s2 = "Error reading NFC tag: ";
                final String value = String.valueOf(e.toString());
                Log.e(s, (value.length() != 0) ? s2.concat(value) : new String(s2));
                if (isSameTag && previousTagWasCardboard) {
                    this.sendDisconnectionEvent();
                }
                return;
            }
            this.mCurrentTagIsCardboard = this.isCardboardNdefMessage(nfcTagContents);
            if (!isSameTag && this.mCurrentTagIsCardboard) {
                synchronized (this.mListeners) {
                    for (final ListenerHelper listener : this.mListeners) {
                        listener.onInsertedIntoCardboard(CardboardDeviceParams.createFromNfcContents(nfcTagContents));
                    }
                }
            }
            if (this.mCurrentTagIsCardboard) {
                this.mTagConnectionFailures = 0;
                (this.mNfcDisconnectTimer = new Timer("NFC disconnect timer")).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        synchronized (NfcSensor.this.mTagLock) {
                            if (!NfcSensor.this.mCurrentNdef.isConnected()) {
                                ++NfcSensor.this.mTagConnectionFailures;
                                if (NfcSensor.this.mTagConnectionFailures > 1) {
                                    NfcSensor.this.closeCurrentNfcTag();
                                    NfcSensor.this.sendDisconnectionEvent();
                                }
                            }
                        }
                    }
                }, 250L, 250L);
            }
        }
    }
    
    private void closeCurrentNfcTag() {
        if (this.mNfcDisconnectTimer != null) {
            this.mNfcDisconnectTimer.cancel();
        }
        if (this.mCurrentNdef == null) {
            return;
        }
        try {
            this.mCurrentNdef.close();
        }
        catch (IOException e) {
            Log.w("NfcSensor", e.toString());
        }
        this.mCurrentTag = null;
        this.mCurrentNdef = null;
        this.mCurrentTagIsCardboard = false;
    }
    
    private void sendDisconnectionEvent() {
        synchronized (this.mListeners) {
            for (final ListenerHelper listener : this.mListeners) {
                listener.onRemovedFromCardboard();
            }
        }
    }
    
    private boolean isCardboardNdefMessage(final NdefMessage message) {
        if (message == null) {
            return false;
        }
        for (final NdefRecord record : message.getRecords()) {
            if (this.isCardboardNdefRecord(record)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isCardboardNdefRecord(final NdefRecord record) {
        if (record == null) {
            return false;
        }
        final Uri uri = record.toUri();
        return uri != null && CardboardDeviceParams.isCardboardUri(uri);
    }
    
    private static class ListenerHelper implements OnCardboardNfcListener
    {
        private OnCardboardNfcListener mListener;
        private Handler mHandler;
        
        public ListenerHelper(final OnCardboardNfcListener listener, final Handler handler) {
            super();
            this.mListener = listener;
            this.mHandler = handler;
        }
        
        public OnCardboardNfcListener getListener() {
            return this.mListener;
        }
        
        @Override
        public void onInsertedIntoCardboard(final CardboardDeviceParams deviceParams) {
            this.mHandler.post((Runnable)new Runnable() {
                @Override
                public void run() {
                    ListenerHelper.this.mListener.onInsertedIntoCardboard(deviceParams);
                }
            });
        }
        
        @Override
        public void onRemovedFromCardboard() {
            this.mHandler.post((Runnable)new Runnable() {
                @Override
                public void run() {
                    ListenerHelper.this.mListener.onRemovedFromCardboard();
                }
            });
        }
    }
    
    public interface OnCardboardNfcListener
    {
        void onInsertedIntoCardboard(CardboardDeviceParams p0);
        
        void onRemovedFromCardboard();
    }
}
