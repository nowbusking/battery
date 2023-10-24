/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery;


public interface OnResponse<T> {
    void onResponse(T object);
    void onFailure(Throwable why);
}
