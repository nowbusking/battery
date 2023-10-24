/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery.annotations;

import com.spoqa.battery.HttpRequest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify callee's HTTP request metadata
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RpcObject {
    final class NULL {}

    int method() default HttpRequest.Methods.GET;
    String uri() default "";
    Class requestSerializer() default NULL.class;
    Class localName() default NULL.class;
    Class remoteName() default NULL.class;
    Class context() default NULL.class;
    String expectedContentType() default "";

}
