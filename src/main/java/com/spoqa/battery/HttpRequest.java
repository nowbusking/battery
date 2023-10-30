/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequest {
    private static final String TAG = "HttpRequest";

    public static final class Methods {
        public static final int GET = 1;
        public static final int POST = 2;
        public static final int PUT = 3;
        public static final int DELETE = 4;
    }

    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    private final int mMethod;
    private final String mUri;
    private final Map<String, String> mHeaders;
    private final Map<String, Object> mParams;
    private byte[] mRequestBody;
    private FieldNameTranslator mFieldNameTranslator;
    private String mContentType;

    public HttpRequest(int method, String uri) {
        mMethod = method;
        mUri = uri;
        mHeaders = new HashMap<>();
        mParams = new HashMap<>();
    }

    public void setRequestBody(byte[] body) {
        mRequestBody = body;
    }

    public void setContentType(String contentType) {
        mContentType = contentType;
    }

    public void putHeader(String key, String value) {
        mHeaders.put(key, value);
    }

    public void putParameters(Map<String, Object> params) {
        for (String key : params.keySet())
            mParams.put(key, params.get(key));
    }

    public void setNameTranslator(FieldNameTranslator fieldNameTranslator) {
        mFieldNameTranslator = fieldNameTranslator;
    }

    public byte[] getRequestBody() {
        return mRequestBody;
    }

    public Map<String, String> getHeaders() {
        return mHeaders;
    }

    public int getMethod() {
        return mMethod;
    }

    public String getContentType() {
        return mContentType;
    }

    public FieldNameTranslator getFieldNameTranslator() {
        return mFieldNameTranslator;
    }

    public String getUri() {
        StringBuilder sb = new StringBuilder();
        sb.append(mUri);

        char delimiter;
        if (mUri.contains("?"))
            delimiter = '&';
        else
            delimiter = '?';

        for (String key : mParams.keySet()) {
            Object value = mParams.get(key);

            if (value instanceof List && value != null) {
                for (Object innerValue : (List<Object>) value) {
                    if (appendQueryString(sb, delimiter, key, innerValue))
                        delimiter = '&';
                }
            } else {
                if (appendQueryString(sb, delimiter, key, value))
                    delimiter = '&';
            }
        }

        String output = sb.toString();
        Logger.debug(TAG, "built uri: " + output);

        return output;
    }

    private boolean appendQueryString(StringBuilder sb, char delimiter, String key, Object value) {
        if (value == null)
            return false;

        try {
            sb.append(String.format("%1$c%2$s=%3$s", delimiter, key,
                    URLEncoder.encode(value.toString(), "utf-8")));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

}
