package com.lotame.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.ads.identifier.AdvertisingIdClient.Info;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 *
 * The MIT License (MIT)
 *
 *  Copyright (c) 2021 Lotame
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * *******************************************************************************
 *
 * Lotame Platform data collection and audience extraction API for Android. This
 * library provides methods to collect, transmit, and extract data managed by
 * the Lotame Platform.
 * 
 * The SDK will attempt to access the Google Advertising ID and the Limit Ad
 * Tracking user preferences.  These are specific to platforms running the
 * Google Play Services 4.0+.  If the SDK determines that the Limit Ad Tracking
 * preference is set to true, the SDK will NOT collect, send, or extract any
 * Crowd Control managed data.
 * 
 * The SDK does provide a mechanism for the client to determine whether or not
 * an Advertising ID was gleaned, {@link #isGoogleAdvertiserIdAvailable()},
 * and whether or not the user has opted-out of ad collection,
 * {@link #isLimitedAdTrackingEnabled()}.
 * 
 * Client code should check that the value of {@link #isInitialized()} is true
 * before attempting to send data or extract audience segments.  Adding data to
 * the CrowdControl instance via {@link #add(String, String)},
 * {@link #addBehavior(long)}, or {@link #addOpportunity(long)} is permitted
 * prior to the CrowdControl instance returning true for
 * {@link #isInitialized()}.
 * 
 * The general pattern of use is:
 * 
 * <pre>
 *  {#code
 *  CrowdControl cc = new CrowdControl(this, CLIENT_ID);
 *
 *  // Add data points to collect.  This can be called any number of times
 *  cc.add("seg", "poweruser");
 *
 *  // Transfer data to Crowd Control servers.  This will transfer all data
 *  // provided via add since the last call to bcp().  Use bcpAsync() for
 *  // asynchronous send.
 *  cc.bcp();
 * }
 * </pre>
 * 
 * By default instances of CrowdControl will make bcp and extraction calls over
 * HTTPS.
 * 
 * Instantiating with an instance of {@link CrowdControl.Protocol}, will enable
 * the use of either http or https.
 * 
 * <pre>
 * {#code
 * // Instantiate a cc instance configured for https calls.
 * CrowdControl cc = new CrowdControl(this, CLIENT_ID, CrowdControl.Protocol.HTTPS);
 *
 * // Determine if the user has opted out of ad collection
 * boolean optedOut = cc.isLimitedAdTrackingEnabled();
 *
 * // The following calls, assuming that optedOut = true, will not collect or
 * // send any data.
 * cc.add("seg", "poweruser");
 * cc.bcp();
 *
 * // The following call will return a null String, again, assuming that the
 * // optedOut variable above is true.
 * cc.getAudienceJSON(5, TimeUnit.SECONDS);
 * }
 * </pre>
 */
public class CrowdControl {
    public static final String LOG_TAG = CrowdControl.class.getSimpleName();

    /*
     * Keys for request parameters
     */
    private static final String KEY_BEHAVIOR_ID = "b";
    private static final String KEY_PAGE_VIEW = "pv";
    private static final String KEY_PLACEMENT_ID = "p";
    private static final String KEY_COUNT_PLACEMENTS = "dp";
    private static final String KEY_CLIENT_ID = "c";
    private static final String KEY_RAND_NUMBER = "rand";
    private static final String KEY_ID = "uid";
    private static final String KEY_ENV_ID = "e";
    private static final String KEY_DEVICE_TYPE = "ua";
    private static final String KEY_SDK_VERSION = "sdk";
    private static final String KEY_PANORAMA_ID =  "rid";

    private static final String SDK_VERSION = "2.0";
    private static final String VALUE_YES = "y";
    private static final String VALUE_APP = "app";
    private static final String BCP_SUBDOMAIN = "bcp.";
    private static final String AE_SUBDOMAIN = "ad.";
    private static final String DEFAULT_DOMAIN = "crwdcntrl.net";
    private static final String BCP_SERVLET = "5";
    private static final String EQUAL = "=";
    private static final String SLASH = "/";

    private static int CONNECTION_TIMEOUT = 5 * 1000;//5 seconds

    private Random random = new Random();
    final private LinkedList<AtomParameter> queue = new LinkedList<>();
    final private Map<String, String> headerParams = new HashMap<>();
    private boolean placementsIncluded = false;
    private boolean sessionTransmitted = false;
    private Context context;
    private StringBuilder url;
    private int clientId = -1;
    private int audienceExtractionClientId = -1;
    private String domain = null;

    protected static boolean debug = false;

    /**
     * The values for id type must match what Lotame supports
     *
     * @author kevin
     */
    public enum IdType {
        // These values match the com.lotame.profile.proto.Profile.proto.DeviceType
        SHA1, GAID;
    }

    /**
     * @author kevin
     */
    public class Id {
        /**
         * The pid used for this device.  It will be either the hashed android id
         * or the Google Play AdvertisingId, depending on the availability of the
         * Google Play service on the specific device on which it is running.
         */
        String mid;
        /**
         * The subsequent id type based on the logic that determines the id.
         */
        IdType idType;
    }

    private Id id;

    /**
     * Indicates whether the Google Play limited ad tracking is enabled.  If
     * we do not have access to the Google Play service then it will be
     * false by default.
     */
    private boolean limitedAdTrackingEnabled;

    private boolean googleAdvertiserIdAvailable;

    // Denotes whether or not to expect the panorama id from the audience extraction api.
    private boolean enablePanoramaId = false;

    private Protocol protocol;
    private final static Protocol PROTOCOL_DEFAULT = Protocol.HTTP;

    Thread setupThread;

    /**
     * Indicates whether or not the instance has been initialized.
     */
    private boolean initialized;

    /**
     * Construct a CrowdControl instance for the supplied client id.  This
     * constructor will instantiate a CrowdControl instance configured to
     * make calls using the {@link #PROTOCOL_DEFAULT} setting.
     *
     * @param ctx Android Context object
     * @param clientId Lotame client id
     */
    public CrowdControl(Context ctx, int clientId) {
        this(ctx, clientId, PROTOCOL_DEFAULT);
    }

    public CrowdControl(Context ctx, int clientId, boolean enablePanoramaId) {
        this(ctx, clientId, clientId, enablePanoramaId);
    }

    public CrowdControl(Context ctx, int clientId, int audienceExtractionClientId) {
        this(ctx, clientId, audienceExtractionClientId, false);
    }

    public CrowdControl(Context ctx, int clientId, int audienceExtractionClientId, boolean enablePanoramaId) {
        init(ctx, clientId, audienceExtractionClientId, PROTOCOL_DEFAULT, DEFAULT_DOMAIN, enablePanoramaId);
    }

    /**
     * Constructs a CrowdControl instance for the supplied client id that
     * is configured with the supplied {@link Protocol} [http|https].
     *
     * @param ctx Android context object
     * @param clientId client id
     * @param protocol http or https
     */
    public CrowdControl(Context ctx, int clientId, Protocol protocol) {
        this(ctx, clientId, protocol, DEFAULT_DOMAIN);
    }

    /**
     * Constructs a CrowdControl instance for the supplied client id that
     * is configured with the supplied {@link Protocol} [http|https] and
     * the supplied first party domain. The domain specified will be
     * prefaced with either "bcp" or "ad" based on the type of call to make.
     *
     * @param ctx Android contect object
     * @param clientId Lotame client id
     * @param protocol http or https
     * @param domain lotame edge domain
     */
    public CrowdControl(Context ctx, int clientId, Protocol protocol, String domain) {
        init(ctx, clientId, clientId, protocol, domain, false);
    }

    /**
     *
     * @param ctx Android context object
     * @param clientId Lotame client id for data collection
     * @param audienceExtractionClientId Lotame client id for audience extraction
     * @param protocol http or https
     * @param enablePanoramaId enable Lotame panorama id
     */
    public CrowdControl(Context ctx, int clientId, int audienceExtractionClientId, Protocol protocol, boolean enablePanoramaId) {
        init(ctx, clientId, audienceExtractionClientId, protocol, DEFAULT_DOMAIN, enablePanoramaId);
    }

    /**
     *
     * @param ctx Android context object
     * @param clientId Lotame client id for data collection
     * @param audienceExtractionClientId Lotame client id for audience extraction
     * @param protocol http or https
     * @param domain Lotame edge domain
     * @param enablePanoramaId enable Lotame panorama id
     */
    public CrowdControl(Context ctx, int clientId, int audienceExtractionClientId, Protocol protocol, String domain, boolean enablePanoramaId) {
        init(ctx, clientId, audienceExtractionClientId, protocol, domain, enablePanoramaId);
    }

    private void init(Context ctx, int clientId, int audienceExtractionClientId, Protocol protocol, String domain, boolean enablePanoramaId) {
        setInitialized(false);
        this.setContext(ctx);
        this.clientId = clientId;
        this.audienceExtractionClientId = audienceExtractionClientId;
        this.protocol = protocol;
        this.domain = domain;
        this.enablePanoramaId = enablePanoramaId;

        //
        // On a separate Thread we will initialize the SDK, getting the id
        // and determining whether or not we are in a limited ad tracking
        // context.
        //
        if (CrowdControl.debug) Log.d(CrowdControl.LOG_TAG, "Setting up the get id thread");
        final Context contextFinal = ctx;
        final Protocol protocolFinal = this.protocol;
        Runnable runnable = new Runnable() {

            public void run() {

                // Set the default values for the ad tracking availability
                // and ad tracking preferences
                setGoogleAdvertiserIdAvailable(false);
                setLimitedAdTrackingEnabled(false);

                // Set the id to a default value that we will be able to use
                // regardless of what happens in the try block.
                String id = Utils.getUuid(contextFinal);
                IdType idType = IdType.SHA1;
                try {
                    Info adInfo = null;
                    adInfo = AdvertisingIdClient.getAdvertisingIdInfo(contextFinal);

                    if (adInfo != null) {
                        if (CrowdControl.debug)
                            Log.d(CrowdControl.LOG_TAG, "We have access to the Google Play, Info instance...");
                        setGoogleAdvertiserIdAvailable(true);
                        setLimitedAdTrackingEnabled(adInfo.isLimitAdTrackingEnabled());
                        id = adInfo.getId();
                        idType = IdType.GAID;
                        if (CrowdControl.debug)
                            Log.d(CrowdControl.LOG_TAG, "AdvertiserId  = " + id);
                        if (CrowdControl.debug)
                            Log.d(CrowdControl.LOG_TAG, "isLimitedAdTrackingEnabled = " + isLimitedAdTrackingEnabled());
                    } else {
                        if (CrowdControl.debug)
                            Log.d(CrowdControl.LOG_TAG, "adInfo is null, unable to access the Google Play AdvertiserId data.  Using the hashed android id and unable to check the ad tracking preferences");
                    }

                } catch (Exception e) {
                    if (CrowdControl.debug)
                        Log.d(CrowdControl.LOG_TAG, "Exception thrown attempting to access Google Play Service to retrieve AdvertiserId data; e = " + e.toString());
                } finally {

                    setIdAndType(id, idType);

                    url = new StringBuilder(protocolFinal.getProtocString() + "://" + BCP_SUBDOMAIN + getDomain() + "/" + BCP_SERVLET + "/");
                    appendParameter(new AtomParameter(KEY_CLIENT_ID, String.valueOf(getClientId())));
                    appendParameter(new AtomParameter(KEY_ID, getId(), AtomParameter.Type.ID));
                    appendParameter(new AtomParameter(KEY_DEVICE_TYPE, getIdType().toString()));
                    appendParameter(new AtomParameter(KEY_ENV_ID, VALUE_APP));

                    if (CrowdControl.debug) Log.d(CrowdControl.LOG_TAG, "using id of " + getId() +
                            "with id type of " + getIdType() +
                            " for client " + getClientId() +
                            " configured for " + getProtocol() +
                            " with url of " + url);

                    startSession();

                    setInitialized(true);
                }
            }
        };

        //
        // Instantiate and run the Thread that will attempt to gather the
        // AdvertiserId data.
        //
        try {
            setupThread = new Thread(runnable);
            if (CrowdControl.debug)
                Log.d(CrowdControl.LOG_TAG, "Starting Thread which will gather id and ad tracking preferences");
            setupThread.start();
        } catch (Exception e) {
            if (CrowdControl.debug)
                Log.e(CrowdControl.LOG_TAG, "Unable to run the thread which determines the id and ad tracking preferences");
            if (CrowdControl.debug) Log.e(CrowdControl.LOG_TAG, "Exception e = " + e.toString());
        }
    }

    /**
     * Set to true to show log messages, or false to hide log messages
     *
     * @param debug defaults to false
     */
    public static void enableDebug(boolean debug) {
        CrowdControl.debug = debug;
    }

    /**
     * @return Object null
     * @deprecated HttpParams is deprecated and should not be used. Use @setRequestProperty instead
     */
    public Object getHttpParams() {
        return null;
    }

    /**
     * Use this method to set the header fields used for http requests to the Crowd Control servers.
     * The following parameters will be ignored and replaced with values supported by CrowdControl:
     * Http Version, Character Set, User Agent (See @URLConnection.setRequestProperty for more details)
     *
     * @param name  the header field
     * @param value the value
     */
    public void setRequestProperty(String name, String value) {
        synchronized (headerParams) {
            headerParams.put(name, value);
        }
    }

    /**
     * Returns the value mapped to the header field.
     *
     * @param name the name of the header field
     * @return the value of the custom header field.  Does not show the overriden values for:
     * Http Version, Character Set, or User Agent
     */
    public String getRequestProperty(String name) {
        return headerParams.get(name);
    }

    /**
     * Will return either the Advertiser ID or the SHA-1 hash of the value
     * returned by the Secure.ANDROID_ID android field.
     * 
     * This method always returns immediately, whether or not the id field
     * has yet been populated by the completion of the construction of the
     * CrowdControl instance.
     *
     * @return the hashed android id string, the Advertiser ID (if
     * available), or null if an id has not yet been generated.
     * (the id is generated asynchronously upon instantiation of the
     * CrowdControl instance).
     */
    public String getId() {
        if (id == null) {
            return null;
        }
        return id.mid;
    }

    private void setIdAndType(String mid, IdType idType) {
        if (id == null) {
            id = new Id();
        }
        this.id.mid = mid;
        this.id.idType = idType;
    }

    public IdType getIdType() {
        if (id == null) {
            return null;
        }
        return id.idType;
    }

    /**
     * Adds key/value to track. This can be called multiple times to add multiple behaviors to track.
     * 
     * If the {@link #isLimitedAdTrackingEnabled()} returns true, this method
     * will return without collecting any data.
     *
     * @param type  the type of the behavior to track
     * @param value the value of the behavior to track
     */
    public void add(String type, String value) {
        if (isLimitedAdTrackingEnabled()) {
            return;
        }

        // AtomParameter is not immutable, so we'll queue them up and build the URL later
        synchronized (queue) {
            if (type.equals(KEY_PLACEMENT_ID)) {
                queue.add(new AtomParameter(type, value, AtomParameter.Type.PLACEMENT_OPPS));
            } else {
                queue.add(new AtomParameter(type, value));
            }
        }
        if (CrowdControl.debug)
            Log.d(CrowdControl.LOG_TAG, "adds type:" + type + " and value:" + value);
    }

    /**
     * Add a behavior to track by id. This is only honored if the CLIENT_ID used to construct the library has access to the behavior with
     * the supplied id. This can be called multiple times to add multiple behaviors to track.
     * 
     * This method, in turn, calls the {@link #add(String, String)} method,
     * which will check the {@link #isLimitedAdTrackingEnabled()}.  If that
     * method returns true in {@link #add(String, String)}, it will return
     * without collecting any data.
     *
     * @param id Lotame behavior id
     */
    public void addBehavior(long id) {
        add(KEY_BEHAVIOR_ID, String.valueOf(id));
    }

    /**
     * Track an opportunity against the placement with the supplied id. This can be called multiple times to track opportunities against
     * multiple placements.
     *
     * @param id the id of the placement
     */
    public void addOpportunity(long id) {
        add(KEY_PLACEMENT_ID, String.valueOf(id));
    }

    /**
     * Synchronously retrieve audience membership. The JSON format is described at
     * <a href="https://my.lotame.com/t/x2hx20x/audience-extraction-api">Audience Extraction API</a>
     * 
     * If {@link #isLimitedAdTrackingEnabled()} returns true, this method
     * will return null without making an audience extraction call.
     * 
     * If {@link #isInitialized()} returns false, the method will also return
     * null without making an extraction call.
     * @param timeout timeout value
     * @param timeUnit timeout value unit
     * @return String the string representation of a JSON object.
     * @throws IOException in case of trouble extracting audiences from Lotame edge servers
     */
    public String getAudienceJSON(long timeout, TimeUnit timeUnit) throws IOException {

        if (isLimitedAdTrackingEnabled()) {
            if (CrowdControl.debug)
                Log.d(CrowdControl.LOG_TAG, "Ad tracking is limited! getAudienceJSON returning blank result.");
            return null;
        }

        String url = null;
        if (enablePanoramaId) {
            url = MessageFormat.format(protocol.getProtocString() +
                            "://" + AE_SUBDOMAIN + getDomain() + "/5/pe=y/c={0}/mid={1}/rid={2}",
                    String.valueOf(getAudienceExtractionClientId()), getId(), VALUE_YES);
        } else {
            url = MessageFormat.format(protocol.getProtocString() +
                            "://" + AE_SUBDOMAIN + getDomain() + "/5/pe=y/c={0}/mid={1}",
                    String.valueOf(getAudienceExtractionClientId()), getId());
        }

        SendOverHTTP sender = new SendOverHTTP(headerParams, CONNECTION_TIMEOUT);
        sender.execute(url);
        try {
            return sender.get(timeout, timeUnit);
        } catch (Exception e) {
            if (CrowdControl.debug)
                Log.e(CrowdControl.LOG_TAG, "Error retrieving audience data", e);
            return null;
        }
    }


    /**
     * Synchronously send the data to the Crowd Control servers. To send
     * asynchronously, use {@link #bcpAsync()}.
     * 
     * If {@link #isLimitedAdTrackingEnabled()} returns true, this method will
     * silently return without collecting any data.
     * 
     * If {@link #isInitialized()} returns false, the method will also silently
     * return without collecting any data.
     *
     * @throws IOException thrown when there the call to Lotame edge servers fails
     */
    public synchronized void bcp() throws IOException {
        if (isLimitedAdTrackingEnabled() || !isInitialized()) {
            return;
        }
        SendOverHTTP sender = new SendOverHTTP(headerParams, CONNECTION_TIMEOUT);
        sender.send(buildBcpUrl());
        synchronized (queue) {
            queue.clear();
        }
        sessionTransmitted = true;
    }

    /**
     * Send the data to the Crowd Control servers.
     * 
     * If {@link #isLimitedAdTrackingEnabled()} returns true, this method will
     * return null without collecting any data.
     * 
     * If {@link #isInitialized()} returns false, the method will also silently
     * return without collecting any data.
     *
     * @return AsyncTask the background task handling the transfer
     */

    public synchronized AsyncTask<String, Void, String> bcpAsync() {
        if (isLimitedAdTrackingEnabled() || !isInitialized()) {
            return null;
        }
        SendOverHTTP sender = new SendOverHTTP(headerParams, CONNECTION_TIMEOUT);
        sender.execute(buildBcpUrl());
        synchronized (queue) {
            queue.clear();
        }
        sessionTransmitted = true;
        return sender;
    }

    /**
     * Send an HTTP or HTTPs request using the supplied URL pattern.
     * This pattern can contain two replacement macros, {deviceid} and {deviceidtype},
     * which will be replaced before performing the HTTP(s) call.
     *
     * @param urlPattern patten to send to Lotame
     * @throws Exception on errors
     */
    public void sendRequest(String urlPattern) throws Exception {

        if (isLimitedAdTrackingEnabled() || !isInitialized()) {
            return;
        }

        if(!getId().isEmpty() && !getIdType().toString().isEmpty()) {

            String newUrlPattern = urlPattern.replace("{deviceid}", getId()).replace("{deviceidtype}", getIdType().toString());

            try {

                final Map<String, String> newUrlPatternParameters = new HashMap<>();

                SendOverHTTP sender = new SendOverHTTP(newUrlPatternParameters, CONNECTION_TIMEOUT);
                sender.execute(newUrlPattern);

            } catch (Exception e) {
                if (CrowdControl.debug)
                    Log.e(CrowdControl.LOG_TAG, "Error Sending sendRequest", e);
            }
        }
    }

    private synchronized String buildBcpUrl() {
        /**
         * Merge the queued data to onto the base url
         */
        StringBuilder builder = new StringBuilder(url);
        append(builder, new AtomParameter(KEY_RAND_NUMBER, String.valueOf(random.nextInt(Integer.MAX_VALUE))));

        synchronized (queue) {
            while (!queue.isEmpty()) {
                AtomParameter param = queue.remove();
                append(builder, param);
                if (!placementsIncluded && AtomParameter.Type.PLACEMENT_OPPS.equals(param.getType())) {
                    append(builder, new AtomParameter(KEY_COUNT_PLACEMENTS, CrowdControl.VALUE_YES));
                    placementsIncluded = true;
                }
            }
        }

        if (!sessionTransmitted) {
            append(builder, new AtomParameter(KEY_PAGE_VIEW, VALUE_YES));
        }
        return builder.toString();
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public int getClientId() {
        return clientId;
    }

    public int getAudienceExtractionClientId() { return audienceExtractionClientId; }

    public boolean isEnablePanoramaId() {return enablePanoramaId; }

    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Indicates whether the CrowdControl instance has completed it's
     * initialization and whether it is ready to send and extract audience
     * data.
     * 
     * Until isInitialized() returns true, no behavior data can be sent, or
     * audience data extracted.  Adding data vi via
     * {@link #add(String, String)}, {@link #addBehavior(long)}, or
     * {@link #addOpportunity(long)} is permitted, and that data will be
     * buffered until the instance is initialized and ready to send and
     * receive data to and from the Lotame Platform.
     *
     * @return - true indicates the instance is completely initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    private void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * Indicates whether the Google Play Advertising preferences have been set
     * to limit advertising tracking.  If the device is not running Google Play
     * Services, or the version is not recent enough for this feature the value
     * will default to false.
     *
     * @return - If discoverable, the users limited ad tracking preferences.  true
     * indicates that the user requested LIMITED ad tracking, false
     * indicates that the user either has not opted-out, or that we
     * were unable to determine whether a preference was set.
     */
    public boolean isLimitedAdTrackingEnabled() {
        return limitedAdTrackingEnabled;
    }

    private void setLimitedAdTrackingEnabled(boolean limitedAdTrackingEnabled) {
        this.limitedAdTrackingEnabled = limitedAdTrackingEnabled;
    }

    /**
     * Indicates whether the SDK was successful in connecting to the Google
     * Play Service and was able to determine the user's Advertising ID and
     * their Limited Ad Tracking preferences.
     *
     * @return - true indicates that the SDK was able to connect to the Google
     * Play Service to extract the Advertiser ID and Limited Ad
     * Tracking preferences.
     */
    public boolean isGoogleAdvertiserIdAvailable() {
        return googleAdvertiserIdAvailable;
    }

    private void setGoogleAdvertiserIdAvailable(boolean googleAdvertiserIdAvailable) {
        this.googleAdvertiserIdAvailable = googleAdvertiserIdAvailable;
    }

    /**
     * Starts new session for collecting user's data. Only the first bcp() call per session will count as a "page view" in your reported
     * stats.
     */
    public void startSession() {
        sessionTransmitted = false;
        if (CrowdControl.debug) Log.d(CrowdControl.LOG_TAG, "Starting new CrowdControl session");
    }

    private void appendParameter(AtomParameter elem) {
        append(url, elem);
    }

    private void append(StringBuilder builder, AtomParameter elem) {
        String value = elem.getValue();
        try {
            value = URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            if (CrowdControl.debug)
                Log.e(CrowdControl.LOG_TAG, "Could not url encode with UTF-8", e);
        }

        builder.append(elem.getKey() + EQUAL
                + ((elem == null || elem.getValue() == null) ? "" : value) + SLASH);
    }

    /**
     * Enumerated type of the protocols supported.
     *
     * @author Ryan Chapin
     * @since 2014-05-06
     */
    public enum Protocol {
        HTTP("http"),
        HTTPS("https");

        private String protocString;

        public String getProtocString() {
            return protocString;
        }

        public static Protocol getProtocol(String protocString) {
            for (Protocol p : Protocol.values()) {
                if (p.getProtocString().equalsIgnoreCase(protocString)) {
                    return p;
                }
            }
            return null;
        }

        private Protocol(String protocString) {
            this.protocString = protocString;
        }
    }

    public String getDomain() {
        return domain;
    }
}
