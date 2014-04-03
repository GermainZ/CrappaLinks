package com.germainz.crappalinks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class Resolver extends Activity {

    private String toastType;
    private static final String TOAST_NONE = "0";
    private static final String TOAST_DETAILED = "2";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPreferences = getSharedPreferences("com.germainz.crappalinks_preferences", Context.MODE_WORLD_READABLE);
        toastType = sharedPreferences.getString("pref_toast_type", TOAST_NONE);
        new ResolveUrl().execute(getIntent().getDataString());
        finish();
    }

    class ResolveUrl extends AsyncTask<String, Void, String> {
        Context context = null;
        boolean connectionError = false;
        boolean noConnectionError = false;

        private ResolveUrl() {
            context = getBaseContext();
        }

        @Override
        protected void onPreExecute() {
            if (!toastType.equals(TOAST_NONE))
                Toast.makeText(context, getString(R.string.toast_message_started), Toast.LENGTH_SHORT).show();
        }

        private String getRedirect(String url) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", "com.germainz.crappalinks");
                c.setRequestProperty("Accept-Encoding", "identity");
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(false);
                c.connect();
                final int responseCode = c.getResponseCode();
                // If the response code is 3xx, it's a redirection. Return the real location.
                if (responseCode >= 300 && responseCode < 400) {
                    String location = c.getHeaderField("Location");
                    if (!new URI(location).isAbsolute())
                        return new URI(url).resolve(location).toString();
                    return location;
                }
                // It might also be a redirection using meta tags. MydealZ uses that.
                else if (c.getURL().getHost().equals("hukd.mydealz.de")) {
                    Document d = Jsoup.parse(c.getInputStream(), "UTF-8", url);
                    Elements refresh = d.select("meta[http-equiv=Refresh]");
                    if (!refresh.isEmpty())
                        return refresh.first().attr("url");
                }
            } catch (ConnectException e) {
                connectionError = true;
            } catch (Exception e) {
                connectionError = true;
                e.printStackTrace();
            } finally {
                if (c != null)
                    c.disconnect();
            }
            return null;
        }

        protected String doInBackground(String... urls) {
            String redirectUrl = urls[0];
            String finalUrl = redirectUrl;

            // if there's no connection, fail and return the original URL.
            if (((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() == null) {
                noConnectionError = true;
                return finalUrl;
            }

            // Keep trying to resolve the URL until we get a URL that isn't a redirect.
            while (redirectUrl != null) {
                redirectUrl = getRedirect(redirectUrl);
                if (redirectUrl != null)
                    finalUrl = redirectUrl;
            }
            return finalUrl;
        }

        protected void onPostExecute(String uri) {
            if (noConnectionError)
                Toast.makeText(context, getString(R.string.toast_message_network) + uri, Toast.LENGTH_LONG).show();
            else if (connectionError)
                Toast.makeText(context, getString(R.string.toast_message_error) + uri, Toast.LENGTH_LONG).show();
            else if (toastType.equals(TOAST_DETAILED))
                Toast.makeText(context, getString(R.string.toast_message_done) + uri, Toast.LENGTH_LONG).show();

            Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(uri));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("crappalinks", "");
            try {
                context.startActivity(intent);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

}