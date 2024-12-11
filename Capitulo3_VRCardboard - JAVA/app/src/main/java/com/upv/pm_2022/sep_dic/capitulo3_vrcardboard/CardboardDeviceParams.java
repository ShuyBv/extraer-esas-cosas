package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

import android.net.*;
import android.util.*;
import java.nio.*;
import com.google.protobuf.nano.*;
import java.io.*;
import android.nfc.*;

public class CardboardDeviceParams{
    private static final String TAG = "CardboardDeviceParams";
    private static final String HTTP_SCHEME = "http";
    private static final String URI_HOST_GOOGLE_SHORT = "g.co";
    private static final String URI_HOST_GOOGLE = "google.com";
    private static final String URI_PATH_CARDBOARD_HOME = "cardboard";
    private static final String URI_PATH_CARDBOARD_CONFIG = "cardboard/cfg";
    private static final String URI_SCHEME_LEGACY_CARDBOARD = "cardboard";
    private static final String URI_HOST_LEGACY_CARDBOARD = "v1.0.0";
    private static final Uri URI_ORIGINAL_CARDBOARD_NFC;
    private static final Uri URI_ORIGINAL_CARDBOARD_QR_CODE;
    private static final String URI_KEY_PARAMS = "p";
    private static final int STREAM_SENTINEL = 894990891;
    private static final String DEFAULT_VENDOR = "Google, Inc.";
    private static final String DEFAULT_MODEL = "Cardboard v1";
    private static final float DEFAULT_INTER_LENS_DISTANCE = 0.06f;
    private static final float DEFAULT_VERTICAL_DISTANCE_TO_LENS_CENTER = 0.035f;
    private static final float DEFAULT_SCREEN_TO_LENS_DISTANCE = 0.042f;
    private String mVendor;
    private String mModel;
    private float mInterLensDistance;
    private float mVerticalDistanceToLensCenter;
    private float mScreenToLensDistance;
    private FieldOfView mLeftEyeMaxFov;
    private boolean mHasMagnet;
    private Distortion mDistortion;
    
    public CardboardDeviceParams() {
        super();
        this.setDefaultValues();
    }
    
    public CardboardDeviceParams(final CardboardDeviceParams params) {
        super();
        this.copyFrom(params);
    }
    
    public CardboardDeviceParams(final CardboardDevice.DeviceParams params) {
        super();
        this.setDefaultValues();
        if (params == null) {
            return;
        }
        this.mVendor = params.getVendor();
        this.mModel = params.getModel();
        this.mInterLensDistance = params.getInterLensDistance();
        this.mVerticalDistanceToLensCenter = params.getTrayBottomToLensHeight();
        this.mScreenToLensDistance = params.getScreenToLensDistance();
        this.mLeftEyeMaxFov = FieldOfView.parseFromProtobuf(params.leftEyeFieldOfViewAngles);
        if (this.mLeftEyeMaxFov == null) {
            this.mLeftEyeMaxFov = new FieldOfView();
        }
        this.mDistortion = Distortion.parseFromProtobuf(params.distortionCoefficients);
        if (this.mDistortion == null) {
            this.mDistortion = new Distortion();
        }
        this.mHasMagnet = params.getHasMagnet();
    }
    
    public static boolean isOriginalCardboardDeviceUri(final Uri uri) {
        return CardboardDeviceParams.URI_ORIGINAL_CARDBOARD_QR_CODE.equals((Object)uri) || (CardboardDeviceParams.URI_ORIGINAL_CARDBOARD_NFC.getScheme().equals(uri.getScheme()) && CardboardDeviceParams.URI_ORIGINAL_CARDBOARD_NFC.getAuthority().equals(uri.getAuthority()));
    }
    
    private static boolean isCardboardDeviceUri(final Uri uri) {
        return HTTP_SCHEME.equals(uri.getScheme()) && URI_HOST_GOOGLE.equals(uri.getAuthority()) && URI_PATH_CARDBOARD_CONFIG.equals(uri.getPath());
    }
    
    public static boolean isCardboardUri(final Uri uri) {
        return isOriginalCardboardDeviceUri(uri) || isCardboardDeviceUri(uri);
    }
    
    public static CardboardDeviceParams createFromUri(final Uri uri) {
        if (uri == null) {
            return null;
        }
        if (isOriginalCardboardDeviceUri(uri)) {
            Log.d("CardboardDeviceParams", "URI recognized as original cardboard device.");
            final CardboardDeviceParams deviceParams = new CardboardDeviceParams();
            deviceParams.setDefaultValues();
            return deviceParams;
        }
        if (!isCardboardDeviceUri(uri)) {
            Log.w("CardboardDeviceParams", String.format("URI \"%s\" not recognized as cardboard device.", uri));
            return null;
        }
        CardboardDevice.DeviceParams params = null;
        final String paramsEncoded = uri.getQueryParameter(URI_KEY_PARAMS);
        if (paramsEncoded != null) {
            try {
                final byte[] bytes = Base64.decode(paramsEncoded, 11);
                params = (CardboardDevice.DeviceParams)MessageNano.mergeFrom((MessageNano)new CardboardDevice.DeviceParams(), bytes);
                Log.d("CardboardDeviceParams", "Read cardboard params from URI.");
            }
            catch (Exception e) {
                final String s = "CardboardDeviceParams";
                final String s2 = "Parsing cardboard parameters from URI failed: ";
                final String value = String.valueOf(e.toString());
                Log.w(s, (value.length() != 0) ? s2.concat(value) : new String(s2));
            }
        }
        else {
            Log.w("CardboardDeviceParams", "No cardboard parameters in URI.");
        }
        return new CardboardDeviceParams(params);
    }
    
    public static CardboardDeviceParams createFromInputStream(final InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try {
            final ByteBuffer header = ByteBuffer.allocate(8);
            if (inputStream.read(header.array(), 0, header.array().length) == -1) {
                Log.e("CardboardDeviceParams", "Error parsing param record: end of stream.");
                return null;
            }
            final int sentinel = header.getInt();
            final int length = header.getInt();
            if (sentinel != STREAM_SENTINEL) {
                Log.e("CardboardDeviceParams", "Error parsing param record: incorrect sentinel.");
                return null;
            }
            final byte[] protoBytes = new byte[length];
            if (inputStream.read(protoBytes, 0, protoBytes.length) == -1) {
                Log.e("CardboardDeviceParams", "Error parsing param record: end of stream.");
                return null;
            }
            return new CardboardDeviceParams((CardboardDevice.DeviceParams)MessageNano.mergeFrom((MessageNano)new CardboardDevice.DeviceParams(), protoBytes));
        }
        catch (InvalidProtocolBufferNanoException e) {
            final String s = "CardboardDeviceParams";
            final String s2 = "Error parsing protocol buffer: ";
            final String value = String.valueOf(e.toString());
            Log.w(s, (value.length() != 0) ? s2.concat(value) : new String(s2));
        }
        catch (IOException e2) {
            final String s3 = "CardboardDeviceParams";
            final String s4 = "Error reading Cardboard parameters: ";
            final String value2 = String.valueOf(e2.toString());
            Log.w(s3, (value2.length() != 0) ? s4.concat(value2) : new String(s4));
        }
        return null;
    }
    
    public boolean writeToOutputStream(final OutputStream outputStream) {
        try {
            final byte[] paramBytes = this.toByteArray();
            final ByteBuffer header = ByteBuffer.allocate(8);
            header.putInt(STREAM_SENTINEL);
            header.putInt(paramBytes.length);
            outputStream.write(header.array());
            outputStream.write(paramBytes);
            return true;
        }
        catch (IOException e) {
            final String s = "CardboardDeviceParams";
            final String s2 = "Error writing Cardboard parameters: ";
            final String value = String.valueOf(e.toString());
            Log.w(s, (value.length() != 0) ? s2.concat(value) : new String(s2));
            return false;
        }
    }
    
    public static CardboardDeviceParams createFromNfcContents(final NdefMessage tagContents) {
        if (tagContents == null) {
            Log.w("CardboardDeviceParams", "Could not get contents from NFC tag.");
            return null;
        }
        for (final NdefRecord record : tagContents.getRecords()) {
            final CardboardDeviceParams params = createFromUri(record.toUri());
            if (params != null) {
                return params;
            }
        }
        return null;
    }

    private byte[] toByteArray() {
        final CardboardDevice.DeviceParams params = new CardboardDevice.DeviceParams();
        params.setVendor(this.mVendor);
        params.setModel(this.mModel);
        params.setInterLensDistance(this.mInterLensDistance);
        params.setTrayBottomToLensHeight(this.mVerticalDistanceToLensCenter);
        params.setScreenToLensDistance(this.mScreenToLensDistance);
        params.leftEyeFieldOfViewAngles = this.mLeftEyeMaxFov.toProtobuf();
        params.distortionCoefficients = this.mDistortion.toProtobuf();
        if (this.mHasMagnet) {
            params.setHasMagnet(this.mHasMagnet);
        }
        return MessageNano.toByteArray((MessageNano)params);
    }
    
    public float getInterLensDistance() {
        return this.mInterLensDistance;
    }
    
    public float getVerticalDistanceToLensCenter() {
        return this.mVerticalDistanceToLensCenter;
    }
    
    public float getScreenToLensDistance() {
        return this.mScreenToLensDistance;
    }
    
    public Distortion getDistortion() {
        return this.mDistortion;
    }
    
    public FieldOfView getLeftEyeMaxFov() {
        return this.mLeftEyeMaxFov;
    }
    
    @Override
    public boolean equals(final Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof CardboardDeviceParams)) {
            return false;
        }
        final CardboardDeviceParams o = (CardboardDeviceParams)other;
        return this.mVendor.equals(o.mVendor) && this.mModel.equals(o.mModel) && this.mInterLensDistance == o.mInterLensDistance && this.mVerticalDistanceToLensCenter == o.mVerticalDistanceToLensCenter && this.mScreenToLensDistance == o.mScreenToLensDistance && this.mLeftEyeMaxFov.equals(o.mLeftEyeMaxFov) && this.mDistortion.equals(o.mDistortion) && this.mHasMagnet == o.mHasMagnet;
    }
    
    private void setDefaultValues() {
        this.mVendor = DEFAULT_VENDOR;
        this.mModel = DEFAULT_MODEL;
        this.mInterLensDistance = DEFAULT_INTER_LENS_DISTANCE;
        this.mVerticalDistanceToLensCenter = DEFAULT_VERTICAL_DISTANCE_TO_LENS_CENTER;
        this.mScreenToLensDistance = DEFAULT_SCREEN_TO_LENS_DISTANCE;
        this.mLeftEyeMaxFov = new FieldOfView();
        this.mHasMagnet = true;
        this.mDistortion = new Distortion();
    }
    
    private void copyFrom(final CardboardDeviceParams params) {
        this.mVendor = params.mVendor;
        this.mModel = params.mModel;
        this.mInterLensDistance = params.mInterLensDistance;
        this.mVerticalDistanceToLensCenter = params.mVerticalDistanceToLensCenter;
        this.mScreenToLensDistance = params.mScreenToLensDistance;
        this.mLeftEyeMaxFov = new FieldOfView(params.mLeftEyeMaxFov);
        this.mHasMagnet = params.mHasMagnet;
        this.mDistortion = new Distortion(params.mDistortion);
    }
    
    static {
        URI_ORIGINAL_CARDBOARD_NFC = new Uri.Builder().scheme(URI_SCHEME_LEGACY_CARDBOARD).authority(URI_HOST_LEGACY_CARDBOARD).build();
        URI_ORIGINAL_CARDBOARD_QR_CODE = new Uri.Builder().scheme(HTTP_SCHEME).authority(URI_HOST_GOOGLE_SHORT).appendEncodedPath(URI_PATH_CARDBOARD_HOME).build();
    }
}
