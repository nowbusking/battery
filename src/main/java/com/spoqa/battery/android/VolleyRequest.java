/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery.android;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.spoqa.battery.HttpRequest;
import com.spoqa.battery.Logger;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class VolleyRequest extends Request<ResponseDelegate> {
    private static final String TAG = "VolleyRequest";

    private Response.Listener<ResponseDelegate> mListener;
    private Map<String, String> mHeaders;
    private byte[] mRequestBody;
    private String mContentType;

    public VolleyRequest(HttpRequest request, Response.Listener<ResponseDelegate> listener,
                         Response.ErrorListener errorListener) {
        super(translateVolleyHttpMethod(request.getMethod()), request.getUri(), errorListener);
        mListener = listener;
        mHeaders = request.getHeaders();
        mRequestBody = request.getRequestBody();
        mContentType = request.getContentType();

        // forbid retry if not GET
        if (request.getMethod() != HttpRequest.Methods.GET) {
            setRetryPolicy(new DefaultRetryPolicy(
                    10000,
                    0,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        }
    }

    static private int translateVolleyHttpMethod(int method) {
        switch (method) {
            case HttpRequest.Methods.GET:
                return Request.Method.GET;
            case HttpRequest.Methods.DELETE:
                return Request.Method.DELETE;
            case HttpRequest.Methods.POST:
                return Request.Method.POST;
            case HttpRequest.Methods.PUT:
                return Request.Method.PUT;
            default:
                Logger.warn(TAG, String.format("Invalid HTTP method %1$d. Defaulting to GET..."));
                return Request.Method.GET;
        }
    }

    @Override
    public Map<String, String> getHeaders() {
        return mHeaders;
    }

    @Override
    protected Response<ResponseDelegate> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            return Response.success(
                    new ResponseDelegate(
                            new String(networkResponse.data, HttpHeaderParser.parseCharset(networkResponse.headers)),
                            networkResponse.headers.get(HttpRequest.HEADER_CONTENT_TYPE)),
                    HttpHeaderParser.parseCacheHeaders(networkResponse));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void deliverResponse(ResponseDelegate response) {
        mListener.onResponse(response);
    }

    @Override
    public String getBodyContentType() {
        return mContentType;
    }

    @Override
    public byte[] getBody() {
        return mRequestBody;
    }

}
