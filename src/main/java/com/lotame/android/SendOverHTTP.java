package com.lotame.android;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
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
 * A utility class for sending data to Crowd Control asynchronously
 */
public class SendOverHTTP extends AsyncTask<String, Void, String> {
    Map<String, String> headerParams;
    int connectionTimeout;

    public SendOverHTTP(Map<String, String> params, int connectionTimeout) {
        headerParams = params;
        this.connectionTimeout = connectionTimeout;
    }

    public String send(String... urls) throws IOException {
        String url = urls[0];
        if (CrowdControl.debug) Log.d(CrowdControl.LOG_TAG, "Attempt GET from " + url);
        HttpURLConnection conn;
        StringBuilder response = new StringBuilder();

        conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setUseCaches(false);
        conn.setConnectTimeout(connectionTimeout);
        conn.setReadTimeout(connectionTimeout);

        synchronized (headerParams) {
            for (Map.Entry<String, String> entry : headerParams.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        conn.setRequestProperty("User-Agent", "Crowd Control Android SDK");
        conn.setRequestProperty("Accept-Charset", "utf-8");

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        try {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                response.append(line);
            }
            if (CrowdControl.debug)
                Log.d(CrowdControl.LOG_TAG, String.format("GET success from %s", url));
        } finally {
            br.close();
            conn.disconnect();
        }

        return response.toString();
    }

    @Override
    protected String doInBackground(String... urls) {
        try {
            return send(urls);
        } catch (Exception e) {
            if (CrowdControl.debug) Log.e(CrowdControl.LOG_TAG, "Async Send Failed", e);
        }

        return "send failed";
    }
}
