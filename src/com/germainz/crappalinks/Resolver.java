package com.germainz.crappalinks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.UnknownHostException;
import java.net.HttpURLConnection;
import java.net.URL;

public class Resolver extends Activity {

    private String toastType;
    private boolean confirmOpen;
    private String resolveAllWhen;
    private boolean useUnshortenIt;
    private static final String TOAST_NONE = "0";
    private static final String TOAST_DETAILED = "2";
    private static final String UNSHORTEN_IT_API_KEY = "UcWGkhtMFdM4019XeI8lgfNOk875RL7K";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPreferences = getSharedPreferences("com.germainz.crappalinks_preferences",
                Context.MODE_WORLD_READABLE);
        toastType = sharedPreferences.getString("pref_toast_type", TOAST_NONE);
        confirmOpen = sharedPreferences.getBoolean("pref_confirm_open", false);
        resolveAllWhen = sharedPreferences.getString("pref_resolve_all_when", "ALWAYS");
        // Still called pref_use_long_url for backwards compatibility, as we used to use longurl.org instead.
        useUnshortenIt = sharedPreferences.getBoolean("pref_use_long_url", false);
        new ResolveUrl().execute(getIntent().getDataString());
        /* Ideally, this would be a service, but we're redirecting intents via Xposed.
         * We finish the activity immediately so that the user can still interact with the
         * foreground app while we unshorten the URL in the background.
         */
        finish();
    }

    private class ResolveUrl extends AsyncTask<String, Void, String> {
        private Context context = null;
        // unknown error while connecting
        private boolean connectionError = false;
        // connection missing/not working
        private boolean noConnectionError = false;

        private ResolveUrl() {
            context = Resolver.this;
        }

        @Override
        protected void onPreExecute() {
            if (!toastType.equals(TOAST_NONE))
                Toast.makeText(context, getString(R.string.toast_message_started),
                        Toast.LENGTH_SHORT).show();
        }

        private String getRedirect(String url) {
            // Used for HEAD request
            HttpURLConnection c1 = null;
            // Used for GET request
            HttpURLConnection c2 = null;
            try {
                c1 = (HttpURLConnection) new URL(url).openConnection();
                c1.setConnectTimeout(10000);
                c1.setReadTimeout(15000);
                // Spare some time in case of a 3xx response code
                c1.setRequestMethod("HEAD");
                c1.connect();
                final int responseCode = c1.getResponseCode();
                // If the response code is 3xx, it's a redirection. Return the real location.
                if (responseCode >= 300 && responseCode < 400) {
                    String location = c1.getHeaderField("Location");
                    return RedirectHelper.getAbsoluteUrl(location, url);
                }
                // It might also be a redirection using meta tags.
                else if (responseCode >= 200 && responseCode < 300 ) {
                    c2 = (HttpURLConnection) new URL(url).openConnection();
                    c2.setConnectTimeout(10000);
                    c2.setReadTimeout(15000);
                    c2.connect();
                    Document d = Jsoup.parse(c2.getInputStream(), "UTF-8", url);
                    Elements refresh = d.select("*:not(noscript) > meta[http-equiv=Refresh]");
                    if (!refresh.isEmpty()) {
                        Element refreshElement = refresh.first();
                        if (refreshElement.hasAttr("url"))
                            return RedirectHelper.getAbsoluteUrl(refreshElement.attr("url"), url);
                        else if (refreshElement.hasAttr("content") && refreshElement.attr("content").contains("url="))
                            return RedirectHelper.getAbsoluteUrl(refreshElement.attr("content").split("url=")[1].replaceAll("^'|'$", ""), url);
                    }
                }
            } catch (ConnectException | UnknownHostException e) {
                noConnectionError = true;
                e.printStackTrace();
            } catch (Exception e) {
                connectionError = true;
                e.printStackTrace();
            } finally {
                if (c1 != null)
                    c1.disconnect();
                if (c2 != null)
                    c2.disconnect();
            }
            return null;
        }

        private String getRedirectUsingLongURL(String url) {
            HttpURLConnection c = null;
            try {
                // http://unshorten.it/api/documentation
                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http").authority("api.unshorten.it").appendQueryParameter("shortURL", url)
                        .appendQueryParameter("responseFormat", "json").appendQueryParameter("apiKey", UNSHORTEN_IT_API_KEY);
                String requestUrl = builder.build().toString();
                c = (HttpURLConnection) new URL(requestUrl).openConnection();
                c.setRequestProperty("User-Agent", "CrappaLinks");
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.connect();
                final int responseCode = c.getResponseCode();
                if (responseCode == 200) {
                    // Response format: {"fullurl": "URL"}
                    JSONObject jsonObject = new JSONObject(new BufferedReader(
                            new InputStreamReader(c.getInputStream())).readLine());
                    if (jsonObject.has("error")) {
                        connectionError = true;
                        Log.e("CrappaLinks", requestUrl);
                        Log.e("CrappaLinks", jsonObject.toString());
                        return url;
                    } else {
                        return jsonObject.getString("fullurl");
                    }
                }
            } catch (ConnectException | UnknownHostException e) {
                noConnectionError = true;
            } catch (Exception e) {
                connectionError = true;
                e.printStackTrace();
            } finally {
                if (c != null)
                    c.disconnect();
            }
            return url;
        }

        protected String doInBackground(String... urls) {
            String redirectUrl = urls[0];

            // if there's no connection, fail and return the original URL.
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (connectivityManager.getActiveNetworkInfo() == null) {
                noConnectionError = true;
                return redirectUrl;
            }

            if (useUnshortenIt) {
                return getRedirectUsingLongURL(redirectUrl);
            } else {
                HttpURLConnection.setFollowRedirects(false);
                // Use the cookie manager so that cookies are stored. Useful for some hosts that keep
                // redirecting us indefinitely unless the set cookie is detected.
                CookieManager cookieManager = new CookieManager();
                CookieHandler.setDefault(cookieManager);

                // Should we resolve all URLs?
                boolean resolveAll = true;
                NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (resolveAllWhen.equals("NEVER") || (resolveAllWhen.equals("WIFI_ONLY") && !wifiInfo.isConnected()))
                    resolveAll = false;

                // Keep trying to resolve the URL until we get a URL that isn't a redirect.
                String finalUrl = redirectUrl;
                while (redirectUrl != null && ((resolveAll) || (RedirectHelper.isRedirect(Uri.parse(redirectUrl).getHost())))) {
                    redirectUrl = getRedirect(redirectUrl);
                    if (redirectUrl != null) {
                        // This should avoid infinite loops, just in case.
                        if (redirectUrl.equals(finalUrl))
                            return finalUrl;
                        finalUrl = redirectUrl;
                    }
                }
                return finalUrl;
            }
        }

        protected void onPostExecute(final String uri) {
            if (noConnectionError)
                Toast.makeText(context, getString(R.string.toast_message_network) + uri, Toast.LENGTH_LONG).show();
            else if (connectionError)
                Toast.makeText(context, getString(R.string.toast_message_error) + uri, Toast.LENGTH_LONG).show();

            if (confirmOpen) {
                Intent confirmDialogIntent = new Intent(context, ConfirmDialog.class);
                confirmDialogIntent.putExtra("uri", uri);
                startActivity(confirmDialogIntent);
            } else {
                if (!noConnectionError && !connectionError && toastType.equals(TOAST_DETAILED))
                    Toast.makeText(context, getString(R.string.toast_message_done) + uri, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("crappalinks", "");
                startActivity(intent);
            }
        }
    }

}
