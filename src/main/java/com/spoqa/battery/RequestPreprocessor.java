/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery;

import com.spoqa.battery.exceptions.ContextException;

public interface RequestPreprocessor {
    void validateContext(Object forWhat) throws ContextException;

    void processHttpRequest(Object object, HttpRequest req);

}
