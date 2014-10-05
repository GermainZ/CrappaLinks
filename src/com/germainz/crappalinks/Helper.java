package com.germainz.crappalinks;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Helper {

    // Hosts that shorten URLs - we need to follow the redirections to get the real URL for those
    private static final String[] REDIRECT_HOSTS = {"t.co", "youtu.be", "bit.ly", "menea.me", "kcy.me", "goo.gl",
            "ow.ly", "j.mp", "redes.li", "dlvr.it", "tinyurl.com", "tmblr.co", "reut.rs", "sns.mx", "wp.me", "4sq.com",
            "ed.cl", "huff.to", "mun.do", "cos.as", "flip.it", "amzn.to", "cort.as", "on.cnn.com", "fb.me",
            "shar.es", "spr.ly", "v.ht", "v0v.in", "redd.it", "bitly.com", "tl.gd", "wh.gov", "hukd.mydealz.de",
            "untp.i", "kck.st", "engt.co", "nyti.ms", "cnnmon.ie", "vrge.co", "is.gd", "cnn.it", "spon.de",
            "affiliation.appgratuites-network.com", "t.cn", "url.cn", "ht.ly", "po.st", "ohmyyy.gt", "dustn.ws",
            "glm.io", "nazr.in", "chip.biz", "ift.tt", "dopice.sk", "phon.es", "buff.ly"};

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
