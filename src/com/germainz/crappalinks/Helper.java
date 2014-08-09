package com.germainz.crappalinks;

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;

public class Helper {

    // Hosts that shorten URLs - we need to follow the redirections to get the real URL for those
    private static final String[] REDIRECT_HOSTS = {"t.co", "youtu.be", "bit.ly", "menea.me", "kcy.me", "goo.gl",
            "ow.ly", "j.mp", "redes.li", "dlvr.it", "tinyurl.com", "tmblr.co", "reut.rs", "sns.mx", "wp.me", "4sq.com",
            "ed.cl", "huff.to", "mun.do", "cos.as", "flip.it", "amzn.to", "cort.as", "on.cnn.com", "fb.me",
            "shar.es", "spr.ly", "v.ht", "v0v.in", "redd.it", "bitly.com", "tl.gd", "wh.gov", "hukd.mydealz.de",
            "untp.i", "kck.st", "engt.co", "nyti.ms", "cnnmon.ie", "vrge.co", "is.gd", "cnn.it", "spon.de",
            "affiliation.appgratuites-network.com", "t.cn", "url.cn", "ht.ly", "po.st", "ohmyyy.gt", "dustn.ws"};

    // Hosts that mask links
    private static final String[] MASK_HOSTS = {"m.facebook.com", "link2.tapatalk.com", "link.tapatalk.com",
            "google.com", "vk.com", "click.linksynergy.com", "youtube.com", "m.scope.am", "redirectingat.com",
            "jdoqocy.com", "viglink.com", "youtube.com", "pt.tapatalk.com"};
    /* If the masked URL is in the form <host>/<segment>, specify that segment
     * for example, Facebook's masked URLs look like http://m.facebook.com/l.php…
     */
    private static final String[] MASK_HOSTS_SEG = {"l.php", null, null, "url", "away.php", null, "attribution_link",
            "api", "rewrite.php", null, "api", "attribution_link", "redirect.php"};
    /* Which parameter should we get?
     * for example, Facebook's masked URLs look like http://m.facebook.com/l.php?u=<actual URL>…
     */
    private static final String[] MASK_HOSTS_PAR = {"u", "url", "out", "q", "to", "RD_PARM1", "u", "out", "url", "url",
            "out", "a", "url"};

    /**
     * Unmask the URI and return it
     */
    public static Uri unmaskLink(Uri uri, int i) {
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

        // Resolve link if it's relative
        try {
            if (!new URI(s).isAbsolute())
                s = new URI(uri.toString()).resolve(s).toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

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
    public static boolean isRedirect(String host) {
        for (String redirectHost : REDIRECT_HOSTS) {
            if (host.endsWith(redirectHost))
                return true;
        }
        return false;
    }

    /**
     * Return the position of the host in MASK_HOSTS, or -1 if it isn't a known URL masker
     */
    public static int getMaskedId(Uri uri) {
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

    public static String getAbsoluteUrl(String urlString, String baseUrlString) throws URISyntaxException,
            MalformedURLException {
        if (urlString.startsWith("http")) {
            return urlString;
        } else {
            URL url = new URL(baseUrlString);
            URI baseUri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
                    url.getPath(), url.getQuery(), url.getRef());
            return baseUri.resolve(urlString).toString();
        }
    }
}
