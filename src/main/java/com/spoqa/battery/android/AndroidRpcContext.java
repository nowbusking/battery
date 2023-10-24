/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery.android;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.toolbox.Volley;

import com.spoqa.battery.CodecUtils;
import com.spoqa.battery.Config;
import com.spoqa.battery.PlatformUtils;
import com.spoqa.battery.RpcContext;
import com.spoqa.battery.FieldNameTranslator;
import com.spoqa.battery.HttpRequest;
import com.spoqa.battery.Logger;
import com.spoqa.battery.ObjectBuilder;
import com.spoqa.battery.OnResponse;
import com.spoqa.battery.RequestFactory;
import com.spoqa.battery.annotations.RpcObject;
import com.spoqa.battery.exceptions.ContextException;
import com.spoqa.battery.exceptions.DeserializationException;
import com.spoqa.battery.exceptions.ResponseValidationException;
import com.spoqa.battery.exceptions.RpcException;
import com.spoqa.battery.exceptions.SerializationException;

import java.nio.charset.StandardCharsets;

public class AndroidRpcContext extends RpcContext<Context> {

    static {
        /* register up */
        Logger.registerLogger(new AndroidLogger());
        PlatformUtils.registerPlatformUtils(new AndroidPlatformUtilsImpl());
    }

    private static final String TAG = "AndroidExecutionContext";

    private final RequestQueue mRequestQueue;
    private final Context mAndroidContext;

    public AndroidRpcContext(Context androidApplicationContext, RequestQueue requestQueue) {
        super();
        mAndroidContext = androidApplicationContext;
        mRequestQueue = requestQueue;
    }

    public AndroidRpcContext(Context androidApplicationContext) {
        this(androidApplicationContext,
                Volley.newRequestQueue(androidApplicationContext, new OkHttpStack()));
    }

    public <T> void invokeAsync(final T rpcObject, final OnResponse<T> onResponse) {
        invokeAsync(rpcObject, onResponse, mAndroidContext);
    }

    public <T> void invokeAsync(final T rpcObject, final OnResponse<T> onResponse, final Context currentContext) {
        HttpRequest request = null;
        try {
            request = RequestFactory.createRequest(this, rpcObject);
        } catch (SerializationException e) {
            onResponse.onFailure(e);
            return;
        } catch (ContextException e) {
            onResponse.onFailure(e.why());
            return;
        }

        if (request == null) {
            Logger.error(TAG, "Could not make call due to error(s) while creating request object.");
            return;
        }

        final RpcObject rpcObjectDecl = rpcObject.getClass().getAnnotation(RpcObject.class);
        if (rpcObjectDecl.context() != RpcObject.NULL.class) {
            Class<?> contextSpec = rpcObjectDecl.context();
            if (!CodecUtils.isSubclassOf(contextSpec, RpcContext.class)) {
                Logger.error(TAG, String.format("Context attribute of RpcObject %1$s is not a " +
                        "subclass of ExecutionContext", rpcObject.getClass().getName()));
                return;
            }
            if (getClass() != contextSpec) {
                Logger.error(TAG, String.format("RpcObject context mismatch. context: %1$s, " +
                        "expected: %2$s", getClass().getName(), contextSpec.getName()));
                return;
            }
        }

        final FieldNameTranslator nameTranslator = request.getFieldNameTranslator();
        Response.Listener<ResponseDelegate> onVolleyResponse = s -> {
            try {
                /* force content type if declared by RpcObject */
                String contentType = rpcObjectDecl.expectedContentType();
                if (contentType == null || contentType.length() == 0)
                    contentType = s.contentType();
                ObjectBuilder.build(contentType, s.data(),
                        rpcObject, nameTranslator, getTypeAdapters());

                if (getResponseValidator() != null) {
                    Object responseObject = CodecUtils.getResponseObject(null, rpcObject, false);
                    if (responseObject == null)
                        responseObject = rpcObject;
                    getResponseValidator().validate(responseObject);
                }
                onResponse.onResponse(rpcObject);
            } catch (ResponseValidationException e) {
                if (!dispatchErrorHandler(currentContext, e)) {
                    onResponse.onFailure(e);
                }
            } catch (RpcException e) {
                e.printStackTrace();
            } catch (DeserializationException e) {
                if (!dispatchErrorHandler(currentContext, e))
                    onResponse.onFailure(e);
            }
        };

        Response.ErrorListener onVolleyErrorResponse = volleyError -> {
            Logger.error(TAG, "Error while RPC call: " + volleyError.getMessage());
            if (Config.DEBUG_DUMP_RESPONSE) {
                try {
                    if (volleyError.networkResponse != null)
                        Logger.error(TAG, "Error response: " +
                                new String(volleyError.networkResponse.data, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    Logger.error(TAG, e.toString());
                }
            }

            Throwable e = volleyError.getCause();
            if (e == null) {
                if (volleyError instanceof ServerError) {
                    e = new RpcException("Server Error");
                } else {
                    e = new RpcException(volleyError.toString());
                }
            }
            if (!dispatchErrorHandler(currentContext, volleyError) &&
                    !dispatchErrorHandler(currentContext, e))
                onResponse.onFailure(volleyError);
        };

        VolleyRequest req = new VolleyRequest(request, onVolleyResponse, onVolleyErrorResponse);
        mRequestQueue.getCache().clear();
        mRequestQueue.add(req);
    }

}
