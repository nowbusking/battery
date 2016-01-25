/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery;

import java.util.HashMap;
import java.util.Map;

public class RpcContext<C> {

    static final private String TAG = "ExecutionContext";

    private String mDefaultUriPrefix;
    private RequestPreprocessor mRequestPreprocessor;
    private ResponseValidator mResponseValidator;
    private RequestSerializer mRequestSerializer;
    private FieldNameTransformer mLocalFieldName;
    private FieldNameTransformer mRemoteFieldName;
    private Map<Class<? extends Throwable>, ExceptionHandler<C>> mExceptionHandlers;
    private TypeAdapterCollection mTypeAdapters;

    public RpcContext() {
        mExceptionHandlers = new HashMap<Class<? extends Throwable>,
                ExceptionHandler<C>>();
        mTypeAdapters = new TypeAdapterCollection();
    }

    public String getDefaultUriPrefix() {
        return mDefaultUriPrefix;
    }

    public RequestPreprocessor getRequestPreprocessor() {
        return mRequestPreprocessor;
    }

    public ResponseValidator getResponseValidator() {
        return mResponseValidator;
    }

    public RequestSerializer getRequestSerializer() {
        return mRequestSerializer;
    }

    public FieldNameTransformer getLocalFieldNameTransformer() {
        return mLocalFieldName;
    }

    public FieldNameTransformer getRemoteFieldNameTransformer() {
        return mRemoteFieldName;
    }

    public void setDefaultUriPrefix(String prefix) {
        if (prefix.startsWith("http://") || prefix.startsWith("https://"))
            mDefaultUriPrefix = prefix;

        // strip out trailing slash
        if (mDefaultUriPrefix.endsWith("/"))
            mDefaultUriPrefix = mDefaultUriPrefix.substring(0, mDefaultUriPrefix.length() - 1);
    }

    public void setRequestPreprocessor(RequestPreprocessor preprocessor) {
        mRequestPreprocessor = preprocessor;
    }

    public void setResponseValidator(ResponseValidator validator) {
        mResponseValidator = validator;
    }

    public void setRequestSerializer(RequestSerializer serializer) {
        mRequestSerializer = serializer;
    }

    public void setFieldNameTransformer(FieldNameTransformer local, FieldNameTransformer remote) {
        mLocalFieldName = local;
        mRemoteFieldName = remote;
    }

    public <T extends Throwable> void registerExceptionHandler(Class<T> clazz, ExceptionHandler<C> handler) {
        mExceptionHandlers.put(clazz, handler);
    }

    public <T extends Throwable> boolean dispatchErrorHandler(C frontendContext, T ex) {
        Class<T> clazz = (Class<T>) ex.getClass();

        if (Config.DEBUG_DUMP_RESPONSE) {
            Logger.debug(TAG, "got exception: " + clazz.getName());
        }

        while (clazz != null) {
            if (mExceptionHandlers.containsKey(clazz)) {
                if (Config.DEBUG_DUMP_RESPONSE) {
                    Logger.debug(TAG, "   handling: " + clazz.getName());
                }

                ExceptionHandler<C> callback = (ExceptionHandler<C>) mExceptionHandlers.get(clazz);
                boolean ret = callback.onException(frontendContext, ex);

                if (ret)
                    return true;
            }

            /* find for superclass */
            Class<?> super_ = clazz.getSuperclass();
            if (!Throwable.class.isAssignableFrom(super_))
                break;

            clazz = (Class<T>) super_;
        }

        return false;
    }

    public TypeAdapterCollection getTypeAdapters() {
        return mTypeAdapters;
    }

}
