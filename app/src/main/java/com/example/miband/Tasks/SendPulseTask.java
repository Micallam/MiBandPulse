package com.example.miband.Tasks;


import android.os.AsyncTask;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;

import javax.net.ssl.HttpsURLConnection;

public class SendPulseTask extends AsyncTask<String, Void, Void> {
    protected Void doInBackground(String... strings) {
        URL url;
        HttpURLConnection conn = null;
        try {
            JSONObject json = new JSONObject();
            json.put("idSimulation", strings[1]);
            json.put("datetime", strings[2]);
            json.put("value", strings[3]);

            url = new URL(strings[0]);
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(20000);
            conn.setConnectTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);


            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter( new OutputStreamWriter(os, "UTF-8"));
            writer.write(encodeParams(json));
            writer.flush();
            writer.close();
            os.close();

            int responseCode=conn.getResponseCode(); // To Check for 200
            if (responseCode == HttpsURLConnection.HTTP_OK) {

                BufferedReader in = new BufferedReader( new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = in.readLine()) != null) {
                    sb.append(line);
                    break;
                }
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            assert conn != null;
            conn.disconnect();
        }
        return null;
    }
    private String encodeParams(JSONObject params) throws Exception {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        Iterator<String> itr = params.keys();
        while(itr.hasNext()){
            String key= itr.next();
            Object value = params.get(key);
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(key, "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(value.toString(), "UTF-8"));
        }
        return result.toString();
    }
}
