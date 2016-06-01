package com.germainz.crappalinks;

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;

public class MaskHost {
    public String URL;
    public String SEGMENT;
    public String PARAMETER;

    public MaskHost(String URL, String SEGMENT, String PARAMETER) {
        this.URL = URL;
        this.SEGMENT = SEGMENT;
        this.PARAMETER = PARAMETER;
    }

    /**
     * Unmask the URI and return it
     */
    public Uri unmaskLink(Uri url) {
        String unmaskedLink = null;
        String path;
        if (SEGMENT == null) {
            // The host always masks, no need to determine if it's the right segment
            unmaskedLink = url.getQueryParameter(PARAMETER);
        } else {
            // Only a certain segment is used to mask URLs. Determine if this is it.
            path = url.getPath();
            if (path == null) return url;
            // If it is, unmask the URL.
            if (path.length() > 0 && path.startsWith(SEGMENT)) {
                if (PARAMETER == null)
                    unmaskedLink = String.format("%s://%s", url.getScheme(),
                            url.getPath().substring(SEGMENT.length()));
                else
                    unmaskedLink = url.getQueryParameter(PARAMETER);
            }
        }

        return parseUrl(url, unmaskedLink);
    }

    /**
     * Resolve relative links if necessary and convert the String URL to a Uri URL.
     */
    public static Uri parseUrl(Uri originalUrl, String newUrl) {
        if (newUrl == null)
            return originalUrl;

        try {
            if (!new URI(newUrl).isAbsolute())
                newUrl= new URI(originalUrl.toString()).resolve(newUrl).toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        try {
            return Uri.parse(URLDecoder.decode(newUrl, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return originalUrl;
        }
    }
}
