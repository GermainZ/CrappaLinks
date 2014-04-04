package com.germainz.crappalinks;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;

public class CrappaLinks implements IXposedHookZygoteInit {

    private final static XSharedPreferences PREFS = new XSharedPreferences("com.germainz.crappalinks");
    private final static boolean PREF_UNSHORTEN_URLS = PREFS.getBoolean("pref_unshorten_urls", true);

    // Hosts that shorten URLs - we need to follow the redirections to get the real URL for those
    private static final String[] REDIRECT_HOSTS = {"t.co", "youtu.be", "bit.ly", "menea.me", "kcy.me", "goo.gl", "ow.ly",
            "j.mp", "redes.li", "dlvr.it", "tinyurl.com", "tmblr.co", "reut.rs", "sns.mx", "wp.me", "4sq.com",
            "ed.cl", "huff.to", "mun.do", "cos.as", "flip.it", "amzn.to", "cort.as", "on.cnn.com", "fb.me",
            "shar.es", "spr.ly", "v.ht", "v0v.in", "redd.it", "bitly.com", "tl.gd", "wh.gov", "hukd.mydealz.de",
            "untp.i", "kck.st", "engt.co", "nyti.ms", "cnnmon.ie", "vrge.co", "is.gd", "cnn.it", "spon.de",
            "affiliation.appgratuites-network.com", "t.cn", "url.cn"};

    // Hosts that mask links
    private static final String[] MASK_HOSTS = {"m.facebook.com", "link2.tapatalk.com", "link.tapatalk.com", "google.com",
            "m.vk.com", "click.linksynergy.com", "youtube.com", "m.scope.am", "redirectingat.com", "jdoqocy.com",
            "viglink.com"};
    // If the masked URL is in the form <host>/<segment>, specify that segment
    // for example, Facebook's masked URLs look like http://m.facebook.com/l.php…
    private static final String[] MASK_HOSTS_SEG = {"l.php", null, null, "url", "away.php", null, "attribution_link", "api",
            "rewrite.php", null, "api"};
    // Which parameter should we get?
    // for example, Facebook's masked URLs look like http://m.facebook.com/l.php?u=<actual URL>…
    private static final String[] MASK_HOSTS_PAR = {"u", "url", "out", "q", "to", "RD_PARM1", "u", "out", "url", "url", "out"};

    public void initZygote(StartupParam startupParam) throws Throwable {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[0];

                // We're only interested in ACTION_VIEW intents.
                String intentAction = intent.getAction();
                if (intentAction == null || !intentAction.equals("android.intent.action.VIEW"))
                    return;

                // If the data isn't a URL (http/https) do nothing.
                Uri intentData = intent.getData();
                if (intentData == null || !intentData.toString().startsWith("http"))
                    return;

                // Unmask the URL (nested masked URLs, too.)
                Uri unmaskedUrl = intentData;
                int i = getMaskedId(unmaskedUrl);
                while (i >= 0) {
                    unmaskedUrl = unmaskLink(unmaskedUrl, i);
                    i = getMaskedId(unmaskedUrl);
                }

                // Does the URL need to be unshortened?
                // The hasExtra check is to check if the URL is sent from our own app after being
                // unshortened, so we,don't start an infinite loop.
                if (PREF_UNSHORTEN_URLS && !intent.hasExtra("crappalinks") && isRedirect(unmaskedUrl.getHost())) {
                    // Have the shortened URL open with our activity. It'll be handled there.
                    intent.setComponent(ComponentName.unflattenFromString("com.germainz.crappalinks/com.germainz.crappalinks.Resolver"));
                }

                // We just set the intent's data to the unmasked URL. If this URL needs to be unshortened,
                // it'll open with our activity (since we explicitly set the component above) which
                // will unshorten it then send a new intent.
                intent.setDataAndType(unmaskedUrl, intent.getType());
            }
        };

        findAndHookMethod("android.app.ContextImpl", null, "startActivity", Intent.class, Bundle.class, hook);
        findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, hook);

    }

    /**
     * Unmask the URI and return it
     */
    private Uri unmaskLink(Uri uri, int i) {
        String s = null;
        List pathSegments;
        if (MASK_HOSTS_SEG[i] == null) {
            // The host always masks, no need to determine if it's the right segment
            s = uri.getQueryParameter(MASK_HOSTS_PAR[i]);
        } else {
            // Only a certain segment is used to mask URLs. Determine if this is it.
            pathSegments = uri.getPathSegments();
            if (pathSegments == null)
                return uri;
            // If it is, unmask the URL.
            if (pathSegments.size() > 0 && MASK_HOSTS_SEG[i].equals(pathSegments.get(0)))
                s = uri.getQueryParameter(MASK_HOSTS_PAR[i]);
        }

        if (s == null)
            return uri;

        try {
            return Uri.parse(URLDecoder.decode(s, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return uri;
        }
    }

    /**
     * Return true if the host is a known URL shortener
     */
    private boolean isRedirect(String host) {
        for (String REDIRECT_HOST : REDIRECT_HOSTS) {
            if (host.equals(REDIRECT_HOST))
                return true;
        }
        return false;
    }

    /**
     * Return the position of the host in MASK_HOSTS, or -1 if it isn't a known URL masker
     */
    private int getMaskedId(Uri uri) {
        String host = uri.getHost();
        for (int i = 0; i < MASK_HOSTS.length; i++) {
            if (host.endsWith(MASK_HOSTS[i])) {
                if (MASK_HOSTS_SEG[i] == null) {
                    return i;
                } else {
                    List pathSegments = uri.getPathSegments();
                    if (pathSegments == null)
                        return -1;
                    if (pathSegments.size() > 0 && MASK_HOSTS_SEG[i].equals(pathSegments.get(0)))
                        return i;
                }
            }
        }
        return -1;
    }

}

