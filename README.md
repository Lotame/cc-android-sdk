[ ![Download](https://api.bintray.com/packages/lotame/cc-android-sdk/cc-android-sdk/images/download.svg) ](https://bintray.com/lotame/cc-android-sdk/cc-android-sdk/_latestVersion)

# Android SDK

This project contains Lotame Platform Android SDK jar to be provided to clients to enable them to more easily send data from Android mobile apps.

## Including library in your project

Add the library as a dependency in gradle, it is in jcenter and maven central:

```
compile 'com.lotame:cc-android-sdk:2.4.0.0@aar'
```

Alternatively, you can build the jar manually from the code with `./gradlew clean jar`. The jar file
will be available in the build\libs directory. Then you can add that jar as a library to another project.

Incorporate the following general pattern into your app to collect and transmit data to Lotame, replacing CLIENT_ID with the id provided by Lotame: 
```
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import android.app.Activity;
import android.os.Bundle;
import com.lotame.android.CrowdControl;
import com.lotame.android.CrowdControl.Protocol;

public class SampleActivity extends Activity {
    private static final int CLIENT_ID = <YourClientID>;
    private static final int TIMEOUT_MILLIS = 5000;
    private CrowdControl ccHttp;
    private CrowdControl ccHttps;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // 'this' can be passed in as the CrowdControl constructor's first
        // argument because this extends android.content.Context.
        // Because no protocol is specified, HTTP will be used.
        ccHttp  = new CrowdControl(this, CLIENT_ID);

        // Instantiating a CrowdControl instance configured for HTTPS
        ccHttps = new CrowdControl(this, CLIENT_ID, Protocol.HTTPS);
    }

    public void collectAndSendSomethingInteresting() throws IOException
    {
        // Add data points to collect.  This can be called any number of times
        ccHttp.add("seg","poweruser");

        if (ccHttp.isInitialized()) {
            // Optional check that the CrowdControl instance has been initialized.
            // Transfer data to Lotame servers.  This will transfer all data
            // provided via add since the last call to bcp().  Use bcpAsync()
            // for asynchronous send.

            ccHttp.bcp();
        }
    }

    /**
    * @return String a JSON representation of the audience info for the current id
    */
    public String getAudienceInfo() throws IOException
    {
        return ccHttp.getAudienceJSON(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        ccHttp.startSession();
    }
}
```
Call startSession() whenever the user initiates a new session by your definition of a session.  For every session, the first call to bcp() or bcpAsync() will count as a "Page View" in the DMP stats.


Additional details for using the library are in the JavaDoc for the [CrowdControl class](src/main/java/com/lotame/android/CrowdControl.java)

### Send an HTTP or HTTPs request using the supplied URL pattern
This pattern can contain two replacement macros, {deviceid} and {deviceidtype}, which will be replaced before performing the HTTP(s) call.
```
ccHttp.sendRequest("http://xyz.com/getData?mid={deviceid}&dt={deviceidtype}");
```

## Maintainers Development Environment Set-up

### Android Studio
To make changes to this project, you must first install the Android Studio http://developer.android.com/tools/studio/index.html

### Open
Once Android Studio is installed, open the sdk project. Android Studio will prompt you for missing requirements such as google play services repository. Follow the steps it suggests.


## Building
Use `./gradlew clean jarRelease` to build a jar file in the build\libs directory.  Modify build.gradle to change the output file name.

## Testing

It is advisable to test the device on an Android Virtual Device (AVD) *AND* on a native device.

Testing is done via the android_demo project

### To install on an AVD:

Click run in Android Studio and choose an emulator

### To install on a native device:

#### Option 1
- First, get your device on the Lotame wireless network.
- Then, on your android device, enable it so that you will be able to install applications from Unknown Sources.
	- Older versions:   Go to Settings > Applications and check the "Unknown Sources" setting to allow you to install the app on your device.
	- 4.x: Go to Settings > Security and check the "Unknown Sources" setting to allow you to install the app on your device.
- Set up an http server on you local machine. If you have python installed, you can cd to the android_demo/bin directory and invoke the following command (this assumes that you have port 8080 open on your firewall).
``` 
python -m SimpleHTTPServer 8080
```
- Then, load your ip address in a browser on your phone.  ``` http://yourip:8080 ```
- Click on the link for the ``` android_demo.apk```, when downloaded your device, click on it and it will ask you what to do with it.  Open it with the package installer and install it on your phone.

#### Option 2

Click run in Android Studio and choose a connected device

### To test: 
- Select a client id that is collecting behaviors via bcp calls in Production.  In this example, we will use 4170.
- Enter the following data in the demo application:
	- client: 4170 Type: b Value: 5990
- Click 'Start New Session' to set up the CrowdControl library
- Click 'Add'
- Click 'Send'
- This should send a bcp call with the male behavior for client 4170
- Click 'Extract'.  Below the 'Start New Session' button you should see a JSON object representing the result of the audience extraction call similar to the following:
```
{"Profile":{"panoramaId": "abcdefghijklmnopqrstuvwxyz1234567890", "tpid":"606d0a5804c0224c7d5b575e73a11b71","Audiences":{"Audience":[{"id":"47280","abbr":"all"}]}}}
```
	- Returning valid JSON indicates a successful test.

## Note to maintainers

To update jcenter/mavencentral, create a `bintray.properties` file in the project's root level with the following information:
```
bintray.user=<your_bintray_username>
bintray.apikey=<your_bintray_apikey>
```

Update the ext.libraryVersion and ext.libraryVersionCode to the desired version numbers in build.gradle.

As long as you are part of the https://bintray.com/lotame organization, you will be able to run the following command
to publish the package:

```
./gradlew clean build install bintrayUpload
```

Then, sync with maven central through the bintray website: https://bintray.com/lotame/cc-android-sdk/cc-android-sdk#central

If you run into trouble, this guide may be helpful: http://crushingcode.nisrulz.com/publish-your-android-library-via-jcenter/


