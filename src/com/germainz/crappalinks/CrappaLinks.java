package com.germainz.crappalinks;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;

public class CrappaLinks implements IXposedHookZygoteInit {
    private final static XSharedPreferences PREFS = new XSharedPreferences("com.germainz.crappalinks");
    private final static boolean PREF_UNSHORTEN_URLS = PREFS.getBoolean("pref_unshorten_urls", true);

    private static final ArrayList<MaskHost> MASK_HOSTS = new ArrayList<>();

    static {
        MASK_HOSTS.add(new MaskHost("m.facebook.com", "l.php", "u"));
        MASK_HOSTS.add(new MaskHost("link.tapatalk.com", null, "out"));
        MASK_HOSTS.add(new MaskHost("link2.tapatalk.com", null, "url"));
        MASK_HOSTS.add(new MaskHost("pt.tapatalk.com", "redirect.php", "url"));
        MASK_HOSTS.add(new MaskHost("google.com", "url", "q"));
        MASK_HOSTS.add(new MaskHost("vk.com", "away.php", "to"));
        MASK_HOSTS.add(new MaskHost("click.linksynergy.com", null, "RD_PARM1"));
        MASK_HOSTS.add(new MaskHost("youtube.com", "attribution_link", "u"));
        MASK_HOSTS.add(new MaskHost("youtube.com", "attribution_link", "a"));
        MASK_HOSTS.add(new MaskHost("m.scope.am", "api", "out"));
        MASK_HOSTS.add(new MaskHost("redirectingat.com", "rewrite.php", "url"));
        MASK_HOSTS.add(new MaskHost("jdoqocy.com", null, "api"));
        MASK_HOSTS.add(new MaskHost("viglink.com", "api", "out"));
        MASK_HOSTS.add(new MaskHost("getpocket.com", "redirect", "url"));
        MASK_HOSTS.add(new MaskHost("news.google.com", "news", "url"));

        // Special hosts below.
        MASK_HOSTS.add(new MaskHost("mandrillapp.com", "track", "p") {
            @Override
            public Uri unmaskLink(Uri url) {
                String b64data = null;
                List pathSegments = url.getPathSegments();
                if (pathSegments == null) return url;
                if (pathSegments.size() > 0 && SEGMENT.equals(pathSegments.get(0)))
                    b64data = url.getQueryParameter(PARAMETER) + "==";
                if (b64data == null) return url;
                String unmaskedLink;
                try {
                    String b64Decoded = new String(Base64.decode(b64data, Base64.URL_SAFE), "UTF-8");
                    JSONObject jsonObject = new JSONObject(b64Decoded);
                    String p = jsonObject.getString("p");
                    JSONObject jsonObject2 = new JSONObject(p);
                    unmaskedLink = jsonObject2.getString("url");
                } catch (JSONException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return url;
                }
                return MaskHost.parseUrl(url, unmaskedLink);
            }
        });
    }

    public void initZygote(StartupParam startupParam) throws Throwable {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[0];
                if (intent = null) return;

                // We're only interested in ACTION_VIEW intents.
                String intentAction = intent.getAction();
                if (intentAction == null || !Intent.ACTION_VIEW.equals(intentAction))
                    return;

                // If the data isn't a URL (http/https) do nothing.
                Uri intentData = intent.getData();
                if (intentData == null || !intentData.toString().startsWith("http"))
                    return;

                // Unmask the URL (nested masked URLs, too.)
                Uri unmaskedUrl = intentData;
                Uri finalUrl = unmaskedUrl;
                MaskHost maskHost = getMaskedUrlMaskHost(unmaskedUrl);
                while (maskHost != null) {
                    unmaskedUrl = maskHost.unmaskLink(unmaskedUrl);
                    if (unmaskedUrl.equals(finalUrl))
                        break;
                    finalUrl = unmaskedUrl;
                    maskHost = getMaskedUrlMaskHost(unmaskedUrl);
                }

                // Does the URL need to be unshortened?
                // The hasExtra check is to check if the URL is sent from our own app after being
                // unshortened, so we,don't start an infinite loop.
                if (PREF_UNSHORTEN_URLS && !intent.hasExtra("crappalinks") &&
                        RedirectHelper.isRedirect(unmaskedUrl.getHost())) {
                    // Have the shortened URL open with our activity. It'll be handled there.
                    intent.setComponent(ComponentName.unflattenFromString(
                            "com.germainz.crappalinks/com.germainz.crappalinks.Resolver"));
                }

                // We just set the intent's data to the unmasked URL. If this URL needs to be unshortened,
                // it'll open with our activity (since we explicitly set the component above) which
                // will unshorten it then send a new intent.
                intent.setDataAndType(unmaskedUrl, intent.getType());
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            findAndHookMethod("android.app.ContextImpl", null, "startActivity", Intent.class, Bundle.class, hook);
            findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, hook);
        } else {
            findAndHookMethod("android.app.ContextImpl", null, "startActivity", Intent.class, hook);
            findAndHookMethod(Activity.class, "startActivity", Intent.class, hook);
        }
    }


    /**
     * Return the appropriate MaskHost or null if the URL's host isn't a known URL masker.
     */
    public static MaskHost getMaskedUrlMaskHost(Uri uri) {
        String host = uri.getHost();
        for (MaskHost maskHost : MASK_HOSTS) {
            if (host.endsWith(maskHost.URL)) {
                if (maskHost.SEGMENT == null) {
                    return maskHost;
                } else {
                    List pathSegments = uri.getPathSegments();
                    if (pathSegments == null)
                        return null;
                    if (pathSegments.size() > 0 && maskHost.SEGMENT.equals(pathSegments.get(0)))
                        return maskHost;
                }
            }
        }
        return null;
    }
}
